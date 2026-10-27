"""
LEXD v1 词典包格式 —— 写入器 & 读取器（Python 参考实现）

布局（小端序）:
  [0,64)     文件头
  [64,64+32N) 段表 (N = 段数)
  ...        段数据

文件头 64B:
  0  char[4]  magic "LXDF"
  4  u16      version = 1
  6  u16      flags
  8  u32      packVer
  12 u32      entryCount
  16 u32      blockSize
  20 u64      buildTime
  28 byte[16] srcHash
  44 u32      crcHeader
  48 u32      sectionCount
  52 12B      reserved

段表项 32B:
  0  char[8]  name
  8  u64      offset
  16 u64      length
  24 u32      crc32
  28 3B       reserved
  31 u8       algo  0=raw 1=zlib

IDX 段:
  u32 blockCount
  u32 blockOffsets[blockCount]     (相对 IDX 段起点)
  blocks...

block:
  u8  entryCount
  u8  reserved
  u16 suffixLen
  u32 payloadOffset                (相对 DAT 段起点)
  u32 payloadLen
  records[entryCount]:
      u8  sharedLen                (与本块上一个词的公共前缀; 首条为 0)
      u8  suffixLen
      bytes[suffixLen] suffix      (UTF-8)
      u32 freqRank

DAT 段: 每块一个 zlib 流，解压后为若干行 JSON-free 文本（字段用 \\x01 分隔）
"""
from __future__ import annotations

import struct
import time
import zlib
from dataclasses import dataclass, field

MAGIC = b"LXDF"
VERSION = 1
HEADER_SIZE = 64
SEC_ENTRY_SIZE = 32
BLOCK_SIZE = 32

SEP_FIELD = "\x01"
SEP_GROUP = "\x02"
SEP_GSENSE = "\x1d"
SEP_SENSE = "\x1f"


def fnv1a(data: bytes) -> int:
    h = 0x811C9DC5
    for b in data:
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


def entry_to_line(e: dict) -> str:
    groups = []
    for g in e["groups"]:
        senses = SEP_GSENSE.join(
            f"{s['d']}{SEP_SENSE}{s.get('tag','')}{SEP_SENSE}{s.get('pri',1)}" for s in g["senses"]
        )
        groups.append(g["p"] + SEP_GSENSE + senses if senses else g["p"])
    exs = SEP_GSENSE.join(f"{x['en']}{SEP_SENSE}{x['zh']}" for x in e.get("examples", []))
    return SEP_FIELD.join([
        e["hw"], e.get("disp", e["hw"]), e.get("ipa_uk", ""), e.get("ipa_us", ""),
        str(e.get("freq", 999999)), ",".join(e.get("levels", [])),
        SEP_GROUP.join(groups), exs,
    ]).replace("\n", " ")


@dataclass
class Section:
    name: str
    data: bytes
    algo: int = 0

    def payload(self) -> bytes:
        return zlib.compress(self.data, 9) if self.algo == 1 else self.data


@dataclass
class PackBuilder:
    entries: list = field(default_factory=list)   # 需已按 hw 排序去重
    forms: dict = field(default_factory=dict)     # form -> headword
    deriv: dict = field(default_factory=dict)     # derived -> (base, type)
    rev: dict = field(default_factory=dict)       # 中文 term -> [(hw, score)]
    rel: dict = field(default_factory=dict)       # 词 -> [短语, ...]（按常用度排序）
    meta: dict = field(default_factory=dict)
    pack_ver: int = 0
    src_hash: bytes = b"\0" * 16

    # ---------- 段编码 ----------
    def _idx_and_dat(self):
        blocks, dat = [], bytearray()
        for i in range(0, len(self.entries), BLOCK_SIZE):
            chunk = self.entries[i:i + BLOCK_SIZE]
            lines = [entry_to_line(e) for e in chunk]
            payload = zlib.compress("\n".join(lines).encode("utf-8"), 9)
            recs = bytearray()
            prev = ""
            for e, line in zip(chunk, lines):
                w = e["hw"]
                shared = 0
                while shared < min(len(prev), len(w), 255) and prev[shared] == w[shared]:
                    shared += 1
                suf = w[shared:].encode("utf-8")
                if len(suf) > 255:
                    raise ValueError(f"headword 过长: {w}")
                recs += struct.pack("<BB", shared, len(suf)) + suf
                recs += struct.pack("<I", int(e.get("freq", 999999)))
                prev = w
            hdr = struct.pack("<BBHII", len(chunk), 0, len(recs), len(dat), len(payload))
            blocks.append(hdr + bytes(recs))
            dat += payload

        idx = bytearray()
        idx += struct.pack("<I", len(blocks))
        off = 4 + 4 * len(blocks)
        offsets, body = [], bytearray()
        for b in blocks:
            offsets.append(off + len(body))
            body += b
        idx += struct.pack(f"<{len(blocks)}I", *offsets) if blocks else b""
        idx += body
        return bytes(idx), bytes(dat)

    def _text_section(self, rows) -> bytes:
        return "\n".join(rows).encode("utf-8")

    def build(self) -> bytes:
        self.entries.sort(key=lambda e: e["hw"])
        idx, dat = self._idx_and_dat()

        secs = [
            Section("IDX", idx, 0),
            # DAT 的每个块已单独 zlib 压缩，整段不能再压一次（algo=0）
            Section("DAT", dat, 0),
            Section("FORMS", self._text_section(f"{k}\t{v}" for k, v in sorted(self.forms.items())), 1),
            Section("DERIV", self._text_section(f"{k}\t{v[0]}\t{v[1]}" for k, v in sorted(self.deriv.items())), 1),
            Section("REV", self._text_section(
                f"{k}\t" + ",".join(f"{hw}:{sc}" for hw, sc in v)
                for k, v in sorted(self.rev.items())
            ), 1),
            Section("REL", self._text_section(
                f"{k}\t" + ",".join(v) for k, v in sorted(self.rel.items())
            ), 1),
            Section("META", self._text_section(
                [f"packVer\t{self.pack_ver}", f"entries\t{len(self.entries)}",
                 f"built\t{int(time.time())}"] + [f"{k}\t{v}" for k, v in self.meta.items()]
            ), 1),
        ]

        table = bytearray()
        body = bytearray()
        base = HEADER_SIZE + SEC_ENTRY_SIZE * len(secs)
        for s in secs:
            data = s.payload()
            table += s.name.encode().ljust(8, b"\0")
            table += struct.pack("<QQI", base + len(body), len(data), zlib.crc32(data))
            table += b"\0\0\0" + struct.pack("<B", s.algo)
            body += data

        hdr = bytearray(HEADER_SIZE)
        hdr[0:4] = MAGIC
        struct.pack_into("<HHI", hdr, 4, VERSION, 0b1, self.pack_ver)
        struct.pack_into("<II", hdr, 12, len(self.entries), BLOCK_SIZE)
        struct.pack_into("<Q", hdr, 20, int(time.time()))
        hdr[28:44] = self.src_hash.ljust(16, b"\0")[:16]
        struct.pack_into("<I", hdr, 44, zlib.crc32(bytes(hdr[0:44])))
        struct.pack_into("<I", hdr, 48, len(secs))
        return bytes(hdr) + bytes(table) + bytes(body)


# ============================ 读取器 ============================

class PackReader:
    def __init__(self, path):
        with open(path, "rb") as f:
            self.buf = f.read()
        self.sections = {}
        magic = self.buf[0:4]
        if magic != MAGIC:
            raise ValueError(f"bad magic {magic!r}")
        self.version, self.flags, self.pack_ver = struct.unpack_from("<HHI", self.buf, 4)
        self.entry_count, self.block_size = struct.unpack_from("<II", self.buf, 12)
        sec_count = struct.unpack_from("<I", self.buf, 48)[0]
        for i in range(sec_count):
            p = HEADER_SIZE + i * SEC_ENTRY_SIZE
            name = self.buf[p:p + 8].rstrip(b"\0").decode()
            off, ln, crc = struct.unpack_from("<QQI", self.buf, p + 8)
            algo = self.buf[p + 31]
            data = self.buf[off:off + ln]
            if zlib.crc32(data) != crc:
                raise ValueError(f"段 {name} CRC 校验失败")
            self.sections[name] = (data, algo)

        idx, _ = self.sections["IDX"]
        self.block_count = struct.unpack_from("<I", idx, 0)[0]
        self.block_offsets = list(struct.unpack_from(f"<{self.block_count}I", idx, 4))
        self._idx = idx

    def _sec(self, name) -> str:
        data, algo = self.sections[name]
        return zlib.decompress(data).decode("utf-8") if algo == 1 else data.decode("utf-8")

    def _block(self, i):
        off = self.block_offsets[i]
        count, _r, suf_len, pay_off, pay_len = struct.unpack_from("<BBHII", self._idx, off)
        p = off + 12
        words, freqs = [], []
        prev = ""
        for _ in range(count):
            shared, sl = struct.unpack_from("<BB", self._idx, p)
            p += 2
            w = prev[:shared] + self._idx[p:p + sl].decode("utf-8")
            p += sl
            f = struct.unpack_from("<I", self._idx, p)[0]
            p += 4
            words.append(w)
            freqs.append(f)
            prev = w
        dat, _ = self.sections["DAT"]
        lines = zlib.decompress(dat[pay_off:pay_off + pay_len]).decode("utf-8").split("\n")
        return words, freqs, lines

    def lookup(self, key: str):
        key = key.strip().lower()
        lo, hi = 0, self.block_count
        while lo < hi:
            mid = (lo + hi) // 2
            first = self._block(mid)[0][0]
            if first <= key:
                lo = mid + 1
            else:
                hi = mid
        if lo == 0:
            return None
        words, _freqs, lines = self._block(lo - 1)
        try:
            i = words.index(key)
        except ValueError:
            return None
        return parse_line(lines[i])

    def forms(self) -> dict:
        out = {}
        for line in self._sec("FORMS").split("\n"):
            if "\t" in line:
                k, v = line.split("\t", 1)
                out[k] = v
        return out

    def deriv(self) -> dict:
        out = {}
        for line in self._sec("DERIV").split("\n"):
            parts = line.split("\t")
            if len(parts) == 3:
                out[parts[0]] = (parts[1], int(parts[2]))
        return out

    def rev(self) -> dict:
        out = {}
        for line in self._sec("REV").split("\n"):
            if "\t" not in line:
                continue
            term, refs = line.split("\t", 1)
            out[term] = [r.rsplit(":", 1) for r in refs.split(",") if ":" in r]
        return out

    def meta(self) -> dict:
        out = {}
        for line in self._sec("META").split("\n"):
            if "\t" in line:
                k, v = line.split("\t", 1)
                out[k] = v
        return out

    def all_headwords(self):
        for i in range(self.block_count):
            yield from self._block(i)[0]


def parse_line(line: str) -> dict:
    f = line.split(SEP_FIELD)
    while len(f) < 8:
        f.append("")
    groups = []
    for g in f[6].split(SEP_GROUP) if f[6] else []:
        parts = g.split(SEP_GSENSE)
        senses = []
        for s in parts[1:]:
            sf = s.split(SEP_SENSE)
            senses.append({"d": sf[0], "tag": sf[1] if len(sf) > 1 else "", "pri": int(sf[2]) if len(sf) > 2 else 1})
        groups.append({"p": parts[0], "senses": senses})
    exs = []
    for x in f[7].split(SEP_GROUP) if f[7] else []:
        xf = x.split(SEP_SENSE)
        exs.append({"en": xf[0], "zh": xf[1] if len(xf) > 1 else ""})
    return {
        "hw": f[0], "disp": f[1], "ipa_uk": f[2], "ipa_us": f[3],
        "freq": int(f[4] or 999999), "levels": f[5].split(",") if f[5] else [],
        "groups": groups, "examples": exs,
    }
