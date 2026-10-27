"""
ECDICT + 技术术语表 → L2.lexd

用法:
  python tools/build_dict.py --ecdict data/raw/ecdict.csv \
      --glossary data/tech/glossary.tsv --out app/src/main/assets/L2.lexd --limit 13000
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from lexd import PackBuilder  # noqa: E402

EXAM_TAGS = {"cet4", "cet6", "ky", "toefl", "ielts", "gre"}

# 短语里允许出现的功能词（不在 13k 核心词表里也要放行）
FUNC_WORDS = {
    "a", "an", "the", "to", "of", "in", "on", "at", "for", "with", "by", "from",
    "up", "down", "out", "off", "into", "onto", "over", "under", "about", "after",
    "before", "as", "and", "or", "not", "no", "be", "is", "are", "was", "were",
    "do", "does", "did", "have", "has", "had", "it", "its", "this", "that", "one",
    "some", "any", "all", "more", "most", "than", "so", "such", "own", "self",
    "away", "back", "through", "across", "along", "around", "against", "between",
    "without", "within", "upon", "per", "via", "vs", "etc",
}

POS_MAP = {
    "n": "n.", "v": "v.", "vt": "v.", "vi": "v.", "adj": "adj.", "a": "adj.",
    "adv": "adv.", "ad": "adv.", "prep": "prep.", "conj": "conj.", "pron": "pron.",
    "num": "num.", "art": "art.", "int": "int.", "aux": "aux.", "abbr": "abbr.",
    "phr": "phr.", "vt/vi": "v.",
}

# exchange 字段编码 → 说明，见 ECDICT 文档
EX_CODES = ("p", "d", "i", "3", "r", "t", "s")

DERIV_PREFIX = ("un", "in", "im", "non", "re", "pre", "post", "over", "under",
                "sub", "super", "hyper", "anti", "co", "de", "inter", "multi")
DERIV_SUFFIX = ("ness", "ment", "tion", "sion", "ation", "ity", "ty", "ance", "ence",
                "al", "ial", "ic", "ical", "ous", "ious", "ive", "ative", "able",
                "ible", "ful", "less", "ish", "ize", "ise", "ify", "ly", "er", "or",
                "ist", "ism")


def norm_pos(raw: str) -> str:
    raw = (raw or "").strip().lower().rstrip(".")
    raw = raw.replace(".", "")
    if not raw:
        return ""
    if raw in POS_MAP:
        return POS_MAP[raw]
    for k, v in POS_MAP.items():
        if raw.startswith(k):
            return v
    return ""


def zh_terms(definition: str):
    """从中文释义里抽可检索的中文关键词（v0.1 用规则，不用分词器）"""
    s = re.sub(r"\[[^\]]*\]", "", definition)
    s = re.sub(r"（[^）]*）|\([^)]*\)", "", s)
    out = []
    for frag in re.split(r"[，,；;、：:。\s/]+", s):
        frag = frag.strip()
        frag = re.sub(r"^[A-Za-z.\s]+", "", frag)
        frag = frag.strip("的了地得之而或")
        if 2 <= len(frag) <= 8 and re.fullmatch(r"[\u4e00-\u9fff]+", frag):
            out.append(frag)
    return out[:4]


def parse_translation(text: str, tech: str = "", tech_pos: str = ""):
    """translation → groups[{p, senses[]}]；tech 释义插到最前"""
    groups: dict[str, list] = {}
    order: list[str] = []
    seen: set[str] = set()

    def norm_key(s: str) -> str:
        return re.sub(r"[\s，,。.；;：:（）()]", "", s)

    def push(pos: str, body: str, tag: str = "", pri: int = 5):
        pos = pos or "misc"
        body = body.strip()
        if not body:
            return
        key = norm_key(body)
        if key in seen:
            return
        # 已有义项包含它（或它包含已有义项）视为重复，避免"幂等的：…"与"幂等"并存
        for k in seen:
            if len(k) >= 2 and (k in key or key in k):
                return
        seen.add(key)
        if pos not in groups:
            groups[pos] = []
            order.append(pos)
        if len(groups[pos]) < 6:
            groups[pos].append({"d": body, "tag": tag, "pri": pri})

    if tech:
        push(tech_pos or "技术", tech, "tech", 1)

    # ECDICT 的 translation 有两种换行：真实换行，以及字面量 "\n"（未转义）
    text = (text or "").replace("\\r", "").replace("\\n", "\n")

    for raw_line in text.split("\n"):
        line = raw_line.strip()
        if not line:
            continue
        tag = ""
        # 领域标记：[计] 计算机 / [化] 化学 / [网络] ...
        m2 = re.match(r"^\[([^\]]+)\]\s*(.+)$", line)
        if m2:
            dom = m2.group(1)
            tag = {"计": "tech", "网络": "net", "数": "math", "医": "med"}.get(dom, "")
            line = m2.group(2).strip()
        m = re.match(r"^([A-Za-z][A-Za-z./&\s]{0,10}?\.)\s*(.+)$", line)
        pos, body = ("", line)
        if m:
            pos, body = norm_pos(m.group(1)), m.group(2)
        for i, sense in enumerate(re.split(r"[；;]", body)):
            sense = re.sub(r"\[[^\]]*\]", "", sense).strip()
            if sense:
                push(pos, sense, tag, 1 + i)
    return [{"p": p, "senses": groups[p]} for p in order]


def parse_exchange(word: str, ex: str):
    """返回 {form: lemma} 中的 form 列表"""
    forms = []
    if not ex:
        return forms
    for part in ex.split("/"):
        if ":" not in part:
            continue
        code, val = part.split(":", 1)
        if code in EX_CODES and val and val != word:
            forms.append(val)
    return forms


# 常用小词优先级：take up / take off 这类最常用的短语动词排前面
PARTICLE_PRIORITY = {
    "up": 0, "off": 1, "out": 2, "on": 3, "in": 4, "over": 5, "back": 6,
    "down": 7, "away": 8, "into": 9, "through": 10, "about": 11, "after": 12,
    "for": 13, "to": 14, "at": 15, "from": 16, "with": 17, "of": 18, "by": 19,
    "round": 20, "along": 21, "across": 22, "apart": 23, "around": 24,
}

PARTICLES = {
    "up", "down", "out", "off", "on", "in", "into", "onto", "over", "through",
    "away", "back", "about", "around", "along", "across", "apart", "by", "for",
    "to", "with", "of", "at", "from", "under", "after", "before", "upon", "against",
}


def collect_phrases(args, core_set: set[str], glossary: dict) -> list[dict]:
    """第二遍扫 CSV，挑出"所有词都在核心词表里"的 2~4 词短语。

    质量门槛：只含 [a-z' -]、无数字、每个 token 在核心词表或功能词表内、有中文释义。
    排序信号（按重要性）：
      ① 首词是动词（ECDICT 的 pos 字段给出词性占比）→ 短语动词优先
      ② 末词是介词/副词小词 → take up / take off 这类
      ③ 最生僻 token 的词频排名（越小越常用）
      ④ 长度、是否来自网络词典、首字母大写（专名）等惩罚项
    """
    word_rank: dict[str, int] = {}
    word_pos: dict[str, set[str]] = {}
    with open(args.ecdict, encoding="utf-8", newline="") as f:
        r = csv.reader(f)
        next(r, None)
        for row in r:
            if len(row) < 13:
                continue
            w = row[0].strip().lower()
            if " " in w or w not in core_set:
                continue
            try:
                f1 = int(row[9] or 0)
                f2 = int(row[8] or 0)
            except ValueError:
                f1 = f2 = 0
            word_rank[w] = f1 if f1 > 0 else (f2 if f2 > 0 else 999999)
            tags = set()
            for part in (row[4] or "").split("/"):
                p, _, ratio = part.partition(":")
                p = p.strip()
                try:
                    if int(ratio or 0) >= 20:
                        tags.add(p)
                except ValueError:
                    pass
            word_pos[w] = tags

    best: dict[str, tuple[int, dict]] = {}
    scanned = 0
    with open(args.ecdict, encoding="utf-8", newline="") as f:
        r = csv.reader(f)
        next(r, None)
        for row in r:
            if len(row) < 13:
                continue
            raw_w = row[0].strip()
            w = raw_w.lower()
            if " " not in w:
                continue
            scanned += 1
            tokens = w.split()
            if not (2 <= len(tokens) <= 4) or len(w) > 42:
                continue
            if not re.fullmatch(r"[a-z][a-z'\- ]*", w):
                continue
            if not all(t in core_set or t in FUNC_WORDS for t in tokens):
                continue
            tr = row[3]
            if not tr or not re.search(r"[\u4e00-\u9fff]", tr):
                continue
            groups = parse_translation(tr)
            if not groups:
                continue

            ranks = [word_rank.get(t, 999999) for t in tokens]
            score = max(ranks)                                   # ③ 生僻词决定门槛
            if "v" in word_pos.get(tokens[0], set()):
                score -= 40000                                   # ① 短语动词
            if tokens[-1] in PARTICLES:
                score -= 25000                                   # ② 小词结尾
            if tokens[-1] in PARTICLE_PRIORITY:
                score += PARTICLE_PRIORITY[tokens[-1]] * 1500     # 常用小词更靠前
            else:
                score += 40000
            if len(tokens) == 2:
                score -= 8000
            if len(tokens) == 4:
                score += 10000
            if tokens[0] in FUNC_WORDS:
                score += 25000                                   # 别以 a/the 开头
            if "[网络]" in tr:
                score += 25000
            if raw_w[:1].isupper():
                score += 50000                                   # 专名/商标

            item = {
                "hw": w,
                "disp": w,
                "ipa_uk": "",
                "ipa_us": "",
                "freq": max(1, min(score, 900000)),
                "levels": ["phrase"] + (["tech"] if w in glossary else []),
                "groups": groups,
                "examples": [],
            }
            prev = best.get(w)
            if prev is None or score < prev[0]:
                best[w] = (score, item)

    ranked = sorted(best.values(), key=lambda x: (x[0], x[1]["hw"]))
    out = [item for _, item in ranked[: args.phrases]]
    print(f"      短语候选 {scanned}，通过质量门槛 {len(best)}，入选 {len(out)}")
    return out


def build_relations(phrases: list[dict], cap: int) -> dict[str, list[str]]:
    """词 → 相关短语（按入选顺序，已按常用度排好）"""
    rel: dict[str, list[str]] = {}
    for e in phrases:
        for t in set(e["hw"].split()):
            lst = rel.setdefault(t, [])
            if len(lst) < cap:
                lst.append(e["hw"])
    return {k: v for k, v in rel.items() if len(v) >= 2}


def read_glossary(path: Path):
    if not path.exists():
        return []
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        en = parts[0].strip().lower()
        rows.append({
            "en": en,
            "pos": parts[1].strip(),
            "zh": parts[2].strip(),
            "note": parts[3].strip() if len(parts) > 3 else "",
        })
    return rows


def build(args) -> None:
    glossary = {g["en"]: g for g in read_glossary(Path(args.glossary))}
    print(f"[1/6] 术语表: {len(glossary)} 条")

    entries: dict[str, dict] = {}
    forms: dict[str, str] = {}
    total = 0

    with open(args.ecdict, encoding="utf-8", newline="") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        for row in reader:
            if len(row) < 13:
                continue
            total += 1
            word, phonetic, _definition, translation, pos, collins, oxford, tag, bnc, frq, exchange = (
                row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[8], row[9], row[10]
            )
            w = word.strip().lower()
            if not w:
                continue
            if " " in w and len(w.split()) > 4:
                continue
            if not re.fullmatch(r"[a-z][a-z'\- .]*", w):
                continue
            tags = {t for t in (tag or "").split() if t}
            try:
                collins_n = int(collins or 0)
                f_frq = int(frq or 0)
                f_bnc = int(bnc or 0)
            except ValueError:
                collins_n, f_frq, f_bnc = 0, 0, 0
            g = glossary.get(w)
            # 先做入选判断，避免为 70 万词条都解析释义（省内存也省时间）
            if not (tags & EXAM_TAGS) and collins_n < 1 and g is None:
                continue
            freq = f_frq if f_frq > 0 else (f_bnc if f_bnc > 0 else 999999)
            if exchange.startswith("0:"):
                continue

            groups = parse_translation(translation, g["zh"] if g else "", g["pos"] if g else "")
            if not groups and not g:
                continue
            levels = ["L2"]
            levels += sorted(tags & EXAM_TAGS)
            if g:
                levels.append("tech")
            if freq <= 8000:
                levels.insert(0, "L1")

            entries[w] = {
                "hw": w,
                "disp": w if w == w.lower() else word.strip(),
                "ipa_uk": phonetic.strip().strip("/[]"),
                "ipa_us": "",
                "freq": freq,
                "levels": levels,
                "groups": groups,
                "examples": [],
                "_tags": tags,
                "_collins": collins_n,
                "_gloss": bool(g),
                "_note": g["note"] if g else "",
            }
            for form in parse_exchange(w, exchange):
                if re.fullmatch(r"[a-z][a-z'\-]*", form):
                    forms[form] = w

    print(f"[2/6] ECDICT 读入 {total} 行，有效词条 {len(entries)}")

    # 术语表里 ECDICT 没有的词，补成独立词条
    added = 0
    for en, g in glossary.items():
        if en in entries:
            continue
        entries[en] = {
            "hw": en, "disp": en, "ipa_uk": "", "ipa_us": "", "freq": 20000,
            "levels": ["L2", "tech"],
            "groups": [{"p": g["pos"] or "技术", "senses": [{"d": g["zh"], "tag": "tech", "pri": 1}]}],
            "examples": [], "_tags": set(), "_collins": 0, "_gloss": True, "_note": g["note"],
        }
        added += 1
    print(f"[3/6] 术语表补充 {added} 条")

    # 选取 L2：考试标签 / 柯林斯星级 / 术语表，按词频排序取前 N
    single = [e for e in entries.values() if " " not in e["hw"]]
    picked = [e for e in single if (e["_tags"] & EXAM_TAGS) or e["_collins"] >= 1 or e["_gloss"]]
    picked.sort(key=lambda e: (e["freq"], e["hw"]))
    if len(picked) > args.limit:
        core = picked[:args.limit]
    else:
        core = picked
    # 术语表词条强制保留
    core_set = {e["hw"] for e in core}
    for e in single:
        if e["_gloss"] and e["hw"] not in core_set:
            core.append(e)
            core_set.add(e["hw"])
    core.sort(key=lambda e: e["hw"])

    phrases = collect_phrases(args, core_set, glossary)
    print(f"[4/6] 入选 L2 {len(core)} 条，短语 {len(phrases)} 条")

    # 词 → 相关短语（供"take 的相关短语"这类入口使用）
    rel = build_relations(phrases, args.rel_cap)
    print(f"      关联索引 {len(rel)} 个词，{sum(len(v) for v in rel.values())} 条关联")

    # 派生词：un-/re-/... + 已有词
    deriv: dict[str, tuple[str, int]] = {}
    headwords = {e["hw"] for e in core}
    for e in core:
        w = e["hw"]
        for p in DERIV_PREFIX:
            if w.startswith(p) and len(w) > len(p) + 2 and w[len(p):] in headwords:
                deriv[w] = (w[len(p):], 1)
                break
        else:
            for s in DERIV_SUFFIX:
                if w.endswith(s) and len(w) > len(s) + 2 and w[:-len(s)] in headwords:
                    deriv[w] = (w[:-len(s)], 2)
                    break
                if w.endswith(s) and len(w) > len(s) + 3 and (w[:-len(s)] + "e") in headwords:
                    deriv[w] = (w[:-len(s)] + "e", 2)
                    break

    # 中文反向索引
    rev: dict[str, list] = {}
    for e in core:
        score_base = 100
        for g in e["groups"]:
            for s in g["senses"]:
                for t in zh_terms(s["d"]):
                    lst = rev.setdefault(t, [])
                    sc = score_base - s["pri"] * 5 - len(t)
                    if all(hw != e["hw"] for hw, _ in lst):
                        lst.append((e["hw"], sc))
                        lst.sort(key=lambda x: -x[1])
                        del lst[12:]

    all_entries = core + phrases
    for e in all_entries:
        for k in ("_tags", "_collins", "_gloss", "_note"):
            e.pop(k, None)

    src = hashlib.sha256()
    with open(args.ecdict, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            src.update(chunk)

    b = PackBuilder(entries=all_entries, forms=forms, deriv=deriv, rev=rev, rel=rel,
                    pack_ver=20261027, src_hash=src.digest())
    b.meta = {"name": "LexForge L2 (TEM-8 近似)", "source": "ECDICT + glossary",
              "level": "L2", "core": str(len(core)), "phrases": str(len(phrases)),
              "relWords": str(len(rel))}
    blob = b.build()
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(blob)
    print(f"[5/6] 写入 {out}  {len(blob)/1024/1024:.2f} MB  (词条 {len(all_entries)}, 变形 {len(forms)}, 派生 {len(deriv)}, 中文索引 {len(rev)}, 关联词 {len(rel)})")
    print(f"[6/6] 完成")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ecdict", default="data/raw/ecdict.csv")
    ap.add_argument("--glossary", default="data/tech/glossary.tsv")
    ap.add_argument("--out", default="app/src/main/assets/L2.lexd")
    ap.add_argument("--limit", type=int, default=13000)
    ap.add_argument("--phrases", type=int, default=120000)
    ap.add_argument("--rel-cap", type=int, default=80)
    build(ap.parse_args())


if __name__ == "__main__":
    main()
