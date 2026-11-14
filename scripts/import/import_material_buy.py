"""把校宝「教材 - 购买记录」页 46 条记录导入 material_buy_record。

- 数据源：data/schoolpal/material_buy_records.json（从校宝表格 DOM 逐行抓取）
- 幂等：按 (ext_order_no, student_name, item_name, quantity, handled_date) 唯一键 upsert，可反复重跑
- 金额：paid_text 为「x.xx元」写入 paid_amount；为「xxx积分」写入 paid_points
"""
import json
import os
import re
import subprocess

BASE = r"d:\Code\Projects\Huancai\data\schoolpal"
MYSQL = r"D:\Code\Projects\Huancai\tools\mysql-8.0.28-winx64\bin\mysql.exe"
DB_ARGS = ["-h", "127.0.0.1", "-P", "3307", "-u", "hc", "-phc123456",
           "-D", "huancai_erp", "--default-character-set=utf8mb4"]
SOURCE = "校宝教材购买记录"

DDL = """
CREATE TABLE IF NOT EXISTS material_buy_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ext_order_no VARCHAR(32) NOT NULL,
  trade_type VARCHAR(32) DEFAULT NULL,
  student_name VARCHAR(64) DEFAULT NULL,
  item_name VARCHAR(255) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  paid_text VARCHAR(64) DEFAULT NULL,
  paid_amount DECIMAL(10,2) DEFAULT NULL,
  paid_points INT DEFAULT NULL,
  receive_status VARCHAR(16) DEFAULT NULL,
  campus_name VARCHAR(64) DEFAULT NULL,
  handler VARCHAR(64) DEFAULT NULL,
  handled_date DATE DEFAULT NULL,
  remark VARCHAR(255) DEFAULT NULL,
  source VARCHAR(32) NOT NULL DEFAULT '校宝教材购买记录',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_buy (ext_order_no, student_name, item_name, quantity, handled_date),
  KEY idx_date (handled_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""


def q(value=None, limit=255):
    if value is None or value == "":
        return "NULL"
    text = "".join(ch for ch in str(value) if ch in "\n\t" or ord(ch) >= 32)
    return "'" + text.replace("\\", "\\\\").replace("'", "''")[:limit] + "'"


def money(text):
    if not text:
        return "NULL"
    m = re.match(r"([0-9.]+)\s*元", text)
    return m.group(1) if m else "NULL"


def points(text):
    if not text:
        return "NULL"
    m = re.match(r"([0-9]+)\s*积分", text)
    return m.group(1) if m else "NULL"


def run(sql):
    p = subprocess.run([MYSQL] + DB_ARGS, input=sql, capture_output=True,
                       text=True, encoding="utf-8")
    if p.returncode != 0:
        raise SystemExit("执行失败：" + p.stderr)
    return p.stdout


def main():
    with open(os.path.join(BASE, "material_buy_records.json"), encoding="utf-8") as f:
        rows = json.load(f)

    stmts = [DDL]
    for row in rows:
        order_no, trade_type, student, item, qty, paid, status, campus, handler, date, remark = row
        stmts.append(
            "INSERT INTO material_buy_record (ext_order_no, trade_type, student_name, item_name, "
            "quantity, paid_text, paid_amount, paid_points, receive_status, campus_name, handler, "
            "handled_date, remark, source) VALUES ("
            f"{q(order_no)}, {q(trade_type)}, {q(student)}, {q(item)}, {int(qty)}, {q(paid)}, "
            f"{money(paid)}, {points(paid)}, {q(status)}, {q(campus)}, {q(handler)}, "
            f"{q(date)}, {q(remark)}, {q(SOURCE)}) "
            "ON DUPLICATE KEY UPDATE trade_type=VALUES(trade_type), paid_text=VALUES(paid_text), "
            "paid_amount=VALUES(paid_amount), paid_points=VALUES(paid_points), "
            "receive_status=VALUES(receive_status), campus_name=VALUES(campus_name), "
            "handler=VALUES(handler), remark=VALUES(remark), source=VALUES(source);"
        )

    run("\n".join(stmts))
    out = run("SELECT COUNT(*), SUM(paid_amount), COUNT(DISTINCT ext_order_no) FROM material_buy_record "
              f"WHERE source = {q(SOURCE)};")
    print("导入完成：", out.strip())


if __name__ == "__main__":
    main()
