"""导入校宝「库存变动记录」82 条。数据源 stock_change.json（11 列，含表头）。幂等。"""
import json
import os
import subprocess

BASE = r"d:\Code\Projects\Huancai\data\schoolpal"
MYSQL = r"D:\Code\Projects\Huancai\tools\mysql-8.0.28-winx64\bin\mysql.exe"
DB_ARGS = ["-h", "127.0.0.1", "-P", "3307", "-u", "hc", "-phc123456",
           "-D", "huancai_erp", "--default-character-set=utf8mb4"]
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_stock_change.sql")

DDL = """
CREATE TABLE IF NOT EXISTS material_stock_change (
  id INT AUTO_INCREMENT PRIMARY KEY,
  room_name VARCHAR(64) NOT NULL,
  material_name VARCHAR(128) NOT NULL,
  biz_type VARCHAR(16) NOT NULL,
  quantity INT NOT NULL DEFAULT 0,
  unit_price DECIMAL(10,2) NOT NULL DEFAULT 0,
  change_date DATE NULL,
  handler VARCHAR(64) NULL,
  student_name VARCHAR(80) NULL,
  ext_order_no VARCHAR(32) NULL,
  remark VARCHAR(255) NULL,
  seq INT NOT NULL DEFAULT 0,
  source VARCHAR(32) NOT NULL DEFAULT '校宝库存变动',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_change (room_name, material_name, biz_type, quantity, change_date, handler, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""

rows = json.load(open(os.path.join(BASE, "stock_change.json"), encoding="utf-8"))


def q(s, n=255):
    if s is None or s == "":
        return "NULL"
    s = "".join(ch for ch in str(s) if ch in "\n\t" or ord(ch) >= 32).strip()
    return "'" + s.replace("\\", "\\\\").replace("'", "''")[:n] + "'" if s else "NULL"


def date(v):
    p = str(v or "").split("-")
    if len(p) != 3:
        return "NULL"
    return "'%04d-%02d-%02d'" % (int(p[0]), int(p[1]), int(p[2]))


def dec(v):
    try:
        return str(round(float(v), 2))
    except (TypeError, ValueError):
        return "0.00"


def integer(v):
    try:
        return str(int(float(v)))
    except (TypeError, ValueError):
        return "0"


vals = []
for i, r in enumerate(rows):
    vals.append("({},{},{},{},{},{},{},{},{},{},{},'校宝库存变动')".format(
        q(r["所属库房"], 64), q(r["教材名称"]), q(r["业务类型"], 16),
        integer(r["数量"]), dec(r["单价"]), date(r["出入库日期"]),
        q(r["经办人"], 64), q(r["学员"], 80), q(r["订单号"], 32), q(r["备注"], 255), i,
    ))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("USE huancai_erp;\n")
    f.write(DDL)
    f.write("DELETE FROM material_stock_change WHERE source = '校宝库存变动';\n")
    f.write("INSERT INTO material_stock_change (room_name, material_name, biz_type, quantity, unit_price, "
            "change_date, handler, student_name, ext_order_no, remark, seq, source) VALUES\n")
    f.write(",\n".join(vals) + ";\n")
    f.write("SELECT COUNT(*) cnt, SUM(quantity > 0) inRows, SUM(quantity) total, "
            "SUM(CASE WHEN quantity > 0 THEN quantity ELSE 0 END) inSum, "
            "SUM(CASE WHEN quantity < 0 THEN -quantity ELSE 0 END) outSum FROM material_stock_change;\n")

with open(OUT, "rb") as f:
    run = subprocess.run([MYSQL] + DB_ARGS, stdin=f, capture_output=True,
                         text=True, encoding="utf-8")
print("rows", len(rows), "out", OUT)
print(run.stdout.strip())
if run.returncode != 0:
    raise SystemExit("导入失败：" + run.stderr)
