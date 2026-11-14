# -*- coding: utf-8 -*-
"""
幻彩课时记账系统 · 单文件后端（零第三方依赖）
运行：python app.py    然后浏览器打开 http://127.0.0.1:8000
局域网：其他设备访问 http://<本机内网IP>:8000
数据：同目录 data.db（SQLite 单文件，拷走就是完整备份）
"""
import json
import os
import socket
import sqlite3
import sys
from datetime import date, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

BASE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE, "data.db")
STATIC = os.path.join(BASE, "static")
PORT = int(os.environ.get("PORT", "8000"))


# ---------------- 数据库 ----------------
def conn():
    c = sqlite3.connect(DB_PATH)
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA foreign_keys=ON")
    return c


def init_db():
    with conn() as c:
        c.executescript("""
        CREATE TABLE IF NOT EXISTS student(
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          name TEXT NOT NULL,
          phone TEXT DEFAULT '',
          campus TEXT DEFAULT '',
          status TEXT DEFAULT '在读',
          note TEXT DEFAULT '',
          created_at TEXT DEFAULT (datetime('now','localtime'))
        );

        CREATE TABLE IF NOT EXISTS enrollment(
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          student_id INTEGER NOT NULL REFERENCES student(id) ON DELETE CASCADE,
          course TEXT NOT NULL,
          total_hours REAL NOT NULL DEFAULT 0,
          gift_hours REAL NOT NULL DEFAULT 0,
          paid REAL NOT NULL DEFAULT 0,
          start_date TEXT DEFAULT '',
          expire_date TEXT DEFAULT '',
          status TEXT DEFAULT '在读',
          note TEXT DEFAULT '',
          created_at TEXT DEFAULT (datetime('now','localtime'))
        );

        CREATE TABLE IF NOT EXISTS hour_txn(
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          enrollment_id INTEGER NOT NULL REFERENCES enrollment(id) ON DELETE CASCADE,
          type TEXT NOT NULL,
          hours REAL NOT NULL,
          note TEXT DEFAULT '',
          created_at TEXT DEFAULT (datetime('now','localtime'))
        );

        CREATE TABLE IF NOT EXISTS orders(
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          no TEXT DEFAULT '',
          student_id INTEGER,
          enrollment_id INTEGER,
          amount_due REAL DEFAULT 0,
          amount_paid REAL DEFAULT 0,
          discount REAL DEFAULT 0,
          pay_method TEXT DEFAULT '微信',
          operator TEXT DEFAULT '',
          note TEXT DEFAULT '',
          created_at TEXT DEFAULT (datetime('now','localtime'))
        );

        CREATE INDEX IF NOT EXISTS idx_txn_enroll ON hour_txn(enrollment_id);
        CREATE INDEX IF NOT EXISTS idx_enroll_stu ON enrollment(student_id);
        """)


def rows(sql, args=()):
    with conn() as c:
        return [dict(r) for r in c.execute(sql, args).fetchall()]


def one(sql, args=()):
    r = rows(sql, args)
    return r[0] if r else None


def exec_sql(sql, args=()):
    with conn() as c:
        cur = c.execute(sql, args)
        return cur.lastrowid


def remaining(eid):
    r = one("SELECT COALESCE(SUM(hours),0) AS h FROM hour_txn WHERE enrollment_id=?", (eid,))
    return round(float(r["h"]), 2)


def enroll_view(e):
    """给报读记录补上剩余课时、折后单价、已消耗金额"""
    rem = remaining(e["id"])
    total = float(e["total_hours"]) or 0.0
    paid = float(e["paid"]) or 0.0
    unit = round(paid / total, 2) if total > 0 else 0.0
    used = round(total - rem, 2)
    if used < 0:
        used = 0.0
    e["remaining"] = rem
    e["unit_price"] = unit
    e["used_hours"] = used
    e["used_amount"] = round(used * unit, 2)
    e["refund_amount"] = round(max(paid - used * unit, 0), 2)
    e["expire_soon"] = False
    if e.get("expire_date"):
        try:
            d = datetime.strptime(e["expire_date"], "%Y-%m-%d").date()
            e["expire_soon"] = 0 <= (d - date.today()).days <= 30
            e["days_left"] = (d - date.today()).days
        except ValueError:
            pass
    return e


# ---------------- API ----------------
def api_summary():
    today = date.today()
    month = today.strftime("%Y-%m")
    stu = one("SELECT COUNT(*) n FROM student WHERE status='在读'")["n"]
    enr = one("SELECT COUNT(*) n FROM enrollment WHERE status='在读'")["n"]
    hours = one("""
        SELECT COALESCE(SUM(h),0) h FROM (
          SELECT e.id, COALESCE(SUM(t.hours),0) h
          FROM enrollment e LEFT JOIN hour_txn t ON t.enrollment_id=e.id
          WHERE e.status='在读' GROUP BY e.id)""")["h"]
    paid = one("SELECT COALESCE(SUM(amount_paid),0) s FROM orders WHERE substr(created_at,1,7)=?", (month,))["s"]
    due = one("SELECT COALESCE(SUM(amount_due-amount_paid),0) s FROM orders")["s"]
    exp = 0
    for e in rows("SELECT * FROM enrollment WHERE status='在读' AND expire_date<>''"):
        v = enroll_view(e)
        if v.get("expire_soon"):
            exp += 1
    recent = rows("""
        SELECT o.*, s.name AS student_name FROM orders o
        LEFT JOIN student s ON s.id=o.student_id
        ORDER BY o.id DESC LIMIT 8""")
    return {
        "students": stu, "enrollments": enr, "hours_left": round(float(hours), 1),
        "month_paid": round(float(paid), 2), "outstanding": round(float(due), 2),
        "expiring": exp, "recent_orders": recent,
    }


def api_students(q=""):
    if q:
        like = "%" + q + "%"
        return rows("SELECT * FROM student WHERE name LIKE ? OR phone LIKE ? ORDER BY id DESC", (like, like))
    return rows("SELECT * FROM student ORDER BY id DESC")


def api_student_detail(sid):
    s = one("SELECT * FROM student WHERE id=?", (sid,))
    if not s:
        return None
    enrs = [enroll_view(e) for e in rows("SELECT * FROM enrollment WHERE student_id=? ORDER BY id DESC", (sid,))]
    txns = rows("""
        SELECT t.*, e.course FROM hour_txn t
        JOIN enrollment e ON e.id=t.enrollment_id
        WHERE e.student_id=? ORDER BY t.id DESC LIMIT 100""", (sid,))
    orders = rows("SELECT * FROM orders WHERE student_id=? ORDER BY id DESC", (sid,))
    return {"student": s, "enrollments": enrs, "txns": txns, "orders": orders}


def api_expiring(days=30):
    out = []
    for e in rows("SELECT e.*, s.name AS student_name, s.phone FROM enrollment e JOIN student s ON s.id=e.student_id WHERE e.status='在读'"):
        v = enroll_view(e)
        d = v.get("days_left")
        if d is not None and d <= days:
            v["student_name"] = e["student_name"]
            out.append(v)
    out.sort(key=lambda x: x.get("days_left", 9999))
    return out


def api_orders():
    return rows("""
        SELECT o.*, s.name AS student_name FROM orders o
        LEFT JOIN student s ON s.id=o.student_id
        ORDER BY o.id DESC LIMIT 300""")


def api_parent(name, phone):
    """家长只读查询：姓名 + 手机号后4位"""
    s = one("SELECT * FROM student WHERE name=? AND phone<>'' AND phone LIKE ?", (name, "%" + phone))
    if not s:
        return {"ok": False, "msg": "没有找到匹配的学员，请核对姓名和手机号后四位"}
    enrs = [enroll_view(e) for e in rows("SELECT * FROM enrollment WHERE student_id=? ORDER BY id DESC", (s["id"],))]
    return {"ok": True, "student": {"name": s["name"], "campus": s["campus"]}, "enrollments": enrs}


def do_refund_calc(eid):
    e = one("SELECT e.*, s.name AS student_name FROM enrollment e JOIN student s ON s.id=e.student_id WHERE e.id=?", (eid,))
    if not e:
        return {"ok": False, "msg": "报读记录不存在"}
    v = enroll_view(e)
    return {"ok": True, "data": v}


# ---------------- HTTP ----------------
class Handler(BaseHTTPRequestHandler):
    server_version = "HuancaiLedger/1.0"

    def log_message(self, fmt, *args):
        pass  # 静音，避免刷屏

    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        if isinstance(body, (dict, list)):
            body = json.dumps(body, ensure_ascii=False, default=str)
        if isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _file(self, name):
        p = os.path.join(STATIC, name)
        if not os.path.exists(p):
            return self._send(404, "not found", "text/plain; charset=utf-8")
        with open(p, "rb") as f:
            data = f.read()
        ctype = "text/html; charset=utf-8" if name.endswith(".html") else "application/octet-stream"
        self._send(200, data, ctype)

    def _body(self):
        n = int(self.headers.get("Content-Length") or 0)
        if not n:
            return {}
        try:
            return json.loads(self.rfile.read(n).decode("utf-8"))
        except Exception:
            return {}

    def do_GET(self):
        u = urlparse(self.path)
        p, q = u.path, parse_qs(u.query)
        try:
            if p in ("/", "/index.html"):
                return self._file("index.html")
            if p in ("/parent", "/parent.html"):
                return self._file("parent.html")

            if p == "/api/summary":
                return self._send(200, api_summary())
            if p == "/api/students":
                return self._send(200, api_students(q.get("q", [""])[0]))
            if p == "/api/student":
                d = api_student_detail(int(q.get("id", ["0"])[0]))
                return self._send(200, d or {"error": "not found"})
            if p == "/api/orders":
                return self._send(200, api_orders())
            if p == "/api/expiring":
                return self._send(200, api_expiring(int(q.get("days", ["30"])[0])))
            if p == "/api/parent":
                return self._send(200, api_parent(q.get("name", [""])[0], q.get("phone", [""])[0]))
            return self._send(404, {"error": "not found"})
        except Exception as e:
            return self._send(500, {"error": str(e)})

    def do_POST(self):
        p = urlparse(self.path).path
        b = self._body()
        try:
            if p == "/api/student":
                sid = exec_sql(
                    "INSERT INTO student(name,phone,campus,status,note) VALUES(?,?,?,?,?)",
                    (b.get("name", "").strip(), b.get("phone", ""), b.get("campus", ""),
                     b.get("status", "在读"), b.get("note", "")))
                return self._send(200, {"ok": True, "id": sid})

            if p == "/api/enroll":
                sid = int(b["student_id"])
                total = float(b.get("total_hours") or 0)
                gift = float(b.get("gift_hours") or 0)
                paid = float(b.get("paid") or 0)
                eid = exec_sql("""INSERT INTO enrollment(student_id,course,total_hours,gift_hours,paid,start_date,expire_date,note)
                                  VALUES(?,?,?,?,?,?,?,?)""",
                               (sid, b.get("course", ""), total, gift, paid,
                                b.get("start_date", ""), b.get("expire_date", ""), b.get("note", "")))
                exec_sql("INSERT INTO hour_txn(enrollment_id,type,hours,note) VALUES(?,?,?,?)",
                         (eid, "报名", total + gift, "报名入账"))
                due = float(b.get("amount_due") or paid + float(b.get("discount") or 0))
                exec_sql("""INSERT INTO orders(no,student_id,enrollment_id,amount_due,amount_paid,discount,pay_method,operator,note)
                            VALUES(?,?,?,?,?,?,?,?,?)""",
                         (b.get("no") or "H" + datetime.now().strftime("%Y%m%d%H%M%S"),
                          sid, eid, due, paid, float(b.get("discount") or 0),
                          b.get("pay_method", "微信"), b.get("operator", ""), b.get("course", "")))
                return self._send(200, {"ok": True, "id": eid})

            if p == "/api/lesson":
                eid = int(b["enrollment_id"])
                hours = float(b.get("hours") or -1)
                exec_sql("INSERT INTO hour_txn(enrollment_id,type,hours,note) VALUES(?,?,?,?)",
                         (eid, b.get("type", "上课"), hours, b.get("note", "")))
                return self._send(200, {"ok": True, "remaining": remaining(eid)})

            if p == "/api/order":
                oid = exec_sql("""INSERT INTO orders(no,student_id,enrollment_id,amount_due,amount_paid,discount,pay_method,operator,note)
                                  VALUES(?,?,?,?,?,?,?,?,?)""",
                               (b.get("no") or "H" + datetime.now().strftime("%Y%m%d%H%M%S"),
                                b.get("student_id"), b.get("enrollment_id"),
                                float(b.get("amount_due") or 0), float(b.get("amount_paid") or 0),
                                float(b.get("discount") or 0), b.get("pay_method", "微信"),
                                b.get("operator", ""), b.get("note", "")))
                return self._send(200, {"ok": True, "id": oid})

            if p == "/api/refund_calc":
                return self._send(200, do_refund_calc(int(b["enrollment_id"])))

            return self._send(404, {"error": "not found"})
        except Exception as e:
            return self._send(500, {"error": str(e)})


def local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def main():
    init_db()
    ip = local_ip()
    print("=" * 52)
    print("  幻彩课时记账系统 已启动")
    print("-" * 52)
    print("  本机访问：   http://127.0.0.1:%d" % PORT)
    print("  局域网访问： http://%s:%d" % (ip, PORT))
    print("  家长查询：   http://%s:%d/parent" % (ip, PORT))
    print("-" * 52)
    print("  数据文件：   %s" % DB_PATH)
    print("  关闭窗口即停止服务")
    print("=" * 52)
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")


if __name__ == "__main__":
    main()
