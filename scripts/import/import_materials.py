"""把校宝「教材设置」436 条 + 「库存查询」三仓库库存 + 「杂费设置」16 条导入本地库。

数据源：
- material_settings.json（436 行：名称/进价/售价/积分兑换/课程类别/状态）
- stock.json（436 行：名称/仓库/鲅鱼圈/新民/总计）
- incidentals.json（16 行：名称/售价/积分兑换/课程类别/状态）

库存表里「下架」教材名称带「（已下架）」后缀，按后缀剥离后与教材设置表 1:1 对齐。
幂等：可反复重跑。
"""
import json
import os
import subprocess

BASE = r"d:\Code\Projects\Huancai\data\schoolpal"
MYSQL = r"D:\Code\Projects\Huancai\tools\mysql-8.0.28-winx64\bin\mysql.exe"
DB_ARGS = ["-h", "127.0.0.1", "-P", "3307", "-u", "hc", "-phc123456",
           "-D", "huancai_erp", "--default-character-set=utf8mb4"]
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_materials.sql")

SUFFIX = "（已下架）"

DDL = """
CREATE TABLE IF NOT EXISTS material (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  cost_price DECIMAL(10,2) NOT NULL DEFAULT 0,
  sale_price DECIMAL(10,2) NOT NULL DEFAULT 0,
  points DECIMAL(10,2) NULL,
  category VARCHAR(64) NULL,
  status VARCHAR(16) NOT NULL DEFAULT '正常',
  qty_warehouse INT NOT NULL DEFAULT 0,
  qty_bayuquan INT NOT NULL DEFAULT 0,
  qty_xinmin INT NOT NULL DEFAULT 0,
  qty_total INT NOT NULL DEFAULT 0,
  source VARCHAR(32) NOT NULL DEFAULT '校宝教材设置',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_material_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS incidental (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  price DECIMAL(10,2) NOT NULL DEFAULT 0,
  points DECIMAL(10,2) NULL,
  category VARCHAR(64) NULL,
  status VARCHAR(16) NOT NULL DEFAULT '正常',
  source VARCHAR(32) NOT NULL DEFAULT '校宝杂费设置',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_incidental_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""


def q(s, n=128):
    if s is None or s == "":
        return "NULL"
    s = "".join(ch for ch in str(s) if ch in "\n\t" or ord(ch) >= 32).strip()
    return "'" + s.replace("\\", "\\\\").replace("'", "''")[:n] + "'"


def dec(v):
    try:
        return "NULL" if v in (None, "") else str(round(float(v), 2))
    except (TypeError, ValueError):
        return "NULL"


settings = json.load(open(os.path.join(BASE, "material_settings.json"), encoding="utf-8"))
fees = json.load(open(os.path.join(BASE, "incidentals.json"), encoding="utf-8"))


def norm(s):
    return "".join(str(s).split())


stock = {norm(r["name"]): r for r in json.load(open(os.path.join(BASE, "stock.json"), encoding="utf-8"))}

vals = []
missing = []
for name, cost, price, points, category, status in settings:
    key = norm(name + (SUFFIX if status == "下架" else ""))
    s = stock.get(key)
    if s is None:
        missing.append(key)
        s = {"warehouse": 0, "bayuquan": 0, "xinmin": 0, "total": 0}
    vals.append(
        "({},{},{},{},{},{},{},{},{},{},'校宝教材设置')".format(
            q(name), dec(cost), dec(price), dec(points), q(category, 64), q(status, 16),
            s["warehouse"], s["bayuquan"], s["xinmin"], s["total"],
        )
    )

fee_vals = []
for name, price, points, category, status in fees:
    fee_vals.append("({},{},{},{},{},'校宝杂费设置')".format(
        q(name), dec(price), dec(points), q(category, 64), q(status, 16)))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("USE huancai_erp;\n")
    f.write(DDL)
    f.write("DELETE FROM material WHERE source = '校宝教材设置';\n")
    f.write("INSERT INTO material (name, cost_price, sale_price, points, category, status, "
            "qty_warehouse, qty_bayuquan, qty_xinmin, qty_total, source) VALUES\n")
    f.write(",\n".join(vals) + ";\n")
    f.write("DELETE FROM incidental WHERE source = '校宝杂费设置';\n")
    f.write("INSERT INTO incidental (name, price, points, category, status, source) VALUES\n")
    f.write(",\n".join(fee_vals) + ";\n")
    f.write("SELECT COUNT(*) materials, SUM(qty_total) stock, SUM(status='正常') onSale FROM material;\n")
    f.write("SELECT COUNT(*) incidentals, SUM(status='正常') onSale FROM incidental;\n")

with open(OUT, "rb") as f:
    run = subprocess.run([MYSQL] + DB_ARGS, stdin=f, capture_output=True,
                         text=True, encoding="utf-8")
print("settings", len(settings), "fees", len(fees), "stockMissing", len(missing), missing[:5])
print(run.stdout.strip())
if run.returncode != 0:
    raise SystemExit("导入失败：" + run.stderr)
