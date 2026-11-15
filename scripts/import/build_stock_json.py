"""把原系统「库存查询」三页 DOM 快照合并成 stock.json（436 行 × 三仓库库存）。

数据源：TEMP/hc-import/recv/stock_p1.json（每页200的第1页）+ stock200_page2/3.json
结构：外表格 nest-table = [教材名称, 总计]；内表格 inner-table = [仓库, 鲅鱼圈, 新民]
"""
import json
import os

RECV = r"C:\Users\chese\AppData\Local\Temp\hc-import\recv"
OUT = r"d:\Code\Projects\Huancai\data\legacy\stock.json"

FILES = [
    ("stock_p1.json", 200),
    ("stock200_page2.json", 200),
    ("stock200_page3.json", 36),
]

rows = []
for fname, expect in FILES:
    d = json.load(open(os.path.join(RECV, fname), encoding="utf-8"))
    outer, inner = d["data"]["tables"][0]["rows"], d["data"]["tables"][1]["rows"]
    assert len(outer) - 1 == expect, (fname, len(outer))
    names = [r[0]["t"] for r in outer[1:]]
    totals = [r[1]["t"] if len(r) > 1 else "" for r in outer[1:]]
    nums = [[c["t"] for c in r][:3] for r in inner[1:]]
    assert len(names) == len(nums), (fname, len(names), len(nums))
    for name, total, trio in zip(names, totals, nums):
        rows.append({
            "name": name,
            "warehouse": int(trio[0]) if trio[0] else 0,
            "bayuquan": int(trio[1]) if trio[1] else 0,
            "xinmin": int(trio[2]) if trio[2] else 0,
            "total": int(total) if total else 0,
        })

bad = [r for r in rows if r["warehouse"] + r["bayuquan"] + r["xinmin"] != r["total"]]
dups = len(rows) - len({r["name"] for r in rows})

print("rows", len(rows), "dupNames", dups, "sumMismatch", len(bad))
if bad[:5]:
    print("mismatch sample", bad[:5])
print("totalSum", sum(r["total"] for r in rows),
      "bayuquan", sum(r["bayuquan"] for r in rows),
      "xinmin", sum(r["xinmin"] for r in rows),
      "warehouse", sum(r["warehouse"] for r in rows))
print("negative", sum(1 for r in rows if r["total"] < 0),
      "zero", sum(1 for r in rows if r["total"] == 0))

json.dump(rows, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("out", OUT, os.path.getsize(OUT))
