"""
生成应用图标 PNG（不依赖 Pillow，纯 zlib 手写 PNG）。

设计：深绿底 + 白色圆角词条卡 + 三行释义（中间一行最深）。
输出 mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher(.round).png
"""
from __future__ import annotations

import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/res"

BG_TOP = (23, 86, 58)       # #17563A
BG_BOTTOM = (40, 130, 90)   # #28825A
CARD = (253, 252, 248)      # #FDFCF8
LINE1 = (78, 156, 119)      # #4E9C77
LINE2 = (14, 59, 39)        # #0E3B27
LINE3 = (127, 195, 160)     # #7FC3A0

SS = 3  # 超采样倍数


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def in_round_rect(x, y, x0, y0, x1, y1, r):
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    cx = min(max(x, x0 + r), x1 - r)
    cy = min(max(y, y0 + r), y1 - r)
    dx, dy = x - cx, y - cy
    return dx * dx + dy * dy <= r * r


def render(size: int, round_bg: bool) -> bytes:
    """返回 RGBA 像素（size x size）"""
    s = size * SS
    px = bytearray(s * s * 4)

    # 设计稿以 108 为坐标系
    def to_design(v):
        return v * s / 108.0

    cx, cy = s / 2, s / 2
    r_out = s / 2

    card = (to_design(24), to_design(26), to_design(84), to_design(82), to_design(10))
    bar1 = (to_design(36), to_design(42), to_design(60), to_design(48), to_design(3))
    bar2 = (to_design(36), to_design(53), to_design(72), to_design(59), to_design(3))
    bar3 = (to_design(36), to_design(64), to_design(64), to_design(70), to_design(3))

    for y in range(s):
        t = y / max(1, s - 1)
        bg = lerp(BG_TOP, BG_BOTTOM, t)
        row = y * s * 4
        for x in range(s):
            i = row + x * 4
            if round_bg:
                dx, dy = x - cx, y - cy
                inside = dx * dx + dy * dy <= r_out * r_out
            else:
                inside = True
            if not inside:
                px[i:i + 4] = b"\x00\x00\x00\x00"
                continue
            col = bg
            if in_round_rect(x, y, *card):
                col = CARD
            if in_round_rect(x, y, *bar2):
                col = LINE2
            elif in_round_rect(x, y, *bar1):
                col = LINE1
            elif in_round_rect(x, y, *bar3):
                col = LINE3
            px[i] = col[0]
            px[i + 1] = col[1]
            px[i + 2] = col[2]
            px[i + 3] = 255

    # 下采样（盒式）
    out = bytearray(size * size * 4)
    n = SS * SS
    for y in range(size):
        for x in range(size):
            r = g = b = a = 0
            for dy in range(SS):
                base = ((y * SS + dy) * s + x * SS) * 4
                for dx in range(SS):
                    i = base + dx * 4
                    r += px[i]
                    g += px[i + 1]
                    b += px[i + 2]
                    a += px[i + 3]
            o = (y * size + x) * 4
            out[o] = r // n
            out[o + 1] = g // n
            out[o + 2] = b // n
            out[o + 3] = a // n
    return bytes(out)


def write_png(path: Path, size: int, rgba: bytes) -> None:
    raw = bytearray()
    stride = size * 4
    for y in range(size):
        raw.append(0)  # filter: none
        raw += rgba[y * stride:(y + 1) * stride]

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    blob = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(blob)


def main() -> None:
    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    for name, px in densities.items():
        for round_bg in (False, True):
            suffix = "_round" if round_bg else ""
            path = OUT / f"mipmap-{name}" / f"ic_launcher{suffix}.png"
            write_png(path, px, render(px, round_bg))
            print(f"{path.relative_to(OUT.parent.parent)}  {px}x{px}")


if __name__ == "__main__":
    main()
