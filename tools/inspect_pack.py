"""
词库包自检 + 查询基准（Python 侧，用于验证打包结果）
  python tools/inspect_pack.py app/src/main/assets/L2.lexd
"""
from __future__ import annotations

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from lexd import PackReader  # noqa: E402


def main() -> None:
    path = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/L2.lexd"
    p = Path(path)
    if not p.exists():
        print(f"找不到 {p}，请先运行 make dict")
        return

    size = p.stat().st_size
    r = PackReader(p)
    print(f"文件      {p}  {size/1024/1024:.2f} MB")
    print(f"格式版本  v{r.version}   packVer={r.pack_ver}")
    print(f"词条数    {r.entry_count}   块数 {r.block_count}  块大小 {r.block_size}")
    print(f"段        {', '.join(f'{k}({len(v[0])/1024:.0f}KB)' for k, v in r.sections.items())}")
    meta = r.meta()
    print("元信息    " + "  ".join(f"{k}={v}" for k, v in meta.items() if k != "built"))

    forms, deriv, rev = r.forms(), r.deriv(), r.rev()
    print(f"变形表    {len(forms)}   派生表 {len(deriv)}   中文索引 {len(rev)}")

    print("\n抽查：")
    for w in ["idempotent", "backpressure", "running", "deprecated", "race condition", "幂等"]:
        e = r.lookup(w)
        if e is None:
            print(f"  {w:<16} 未命中")
        else:
            d = e["groups"][0]["senses"][0]["d"] if e["groups"] else ""
            print(f"  {w:<16} {e['disp']:<16} {d[:44]}")

    # 基准：命中路径
    words = list(r.all_headwords())
    sample = words[:: max(1, len(words) // 3000)][:3000]
    for w in sample[:300]:
        r.lookup(w)
    t0 = time.perf_counter()
    hits = sum(1 for w in sample if r.lookup(w) is not None)
    dt = (time.perf_counter() - t0) / len(sample) * 1000
    print(f"\n查询基准  {len(sample)} 次，命中 {hits}，平均 {dt:.3f} ms/次（含 Python 解压，非端上真实值）")

    # 中文反查抽查
    for zh in ["幂等", "竞态", "缓存", "序列化"]:
        refs = rev.get(zh)
        print(f"  中文 {zh:<6} -> {refs[:3] if refs else '无'}")


if __name__ == "__main__":
    main()
