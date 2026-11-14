# -*- coding: utf-8 -*-
"""演示数据：验证报名 → 记上课 → 退费试算 → 家长查询 全链路"""
import json
import urllib.request

BASE = "http://127.0.0.1:8000"


def post(path, body):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode())


STUDENTS = [
    ("李玥琳", "138****2424", "鲅鱼圈幻彩国学堂"),
    ("周俊辰", "139****5678", "鲅鱼圈幻彩国学堂"),
    ("马钧晟", "137****9012", "鲅鱼圈幻彩国学堂"),
    ("吴城江", "136****3456", "新民幻彩国学堂"),
    ("王思元", "135****7890", "鲅鱼圈幻彩国学堂"),
]

# 真实手机号后四位便于家长查询演示
PHONES = ["2424", "5678", "9012", "3456", "7890"]

COURSES = [
    ("【语文】4年级 房10:10", 20, 1, 1500, "2026-10-01", "2027-10-01"),
    ("【语文】6年级 房16:30", 20, 0, 2000, "2026-09-05", "2027-09-05"),
    ("【阅读】5-6年级 房18:30", 16, 0, 1280, "2026-09-04", "2026-11-10"),
    ("【名著阅读】5、6年级", 12, 2, 960, "2026-08-20", "2026-10-25"),
    ("【作文】3、4年级 韩13:30", 10, 0, 900, "2026-10-10", "2027-04-10"),
]


def main():
    ids = []
    for (name, _, campus), ph in zip(STUDENTS, PHONES):
        r = post("/api/student", {"name": name, "phone": "13x" + ph, "campus": campus})
        ids.append(r["id"])

    for i, sid in enumerate(ids):
        course, total, gift, paid, start, exp = COURSES[i % len(COURSES)]
        r = post("/api/enroll", {
            "student_id": sid, "course": course,
            "total_hours": total, "gift_hours": gift,
            "paid": paid, "discount": 0,
            "start_date": start, "expire_date": exp,
            "pay_method": ["微信", "支付宝", "现金", "银行转账", "POS"][i % 5],
            "operator": "王博",
        })
        # 记几次上课
        eid = r["id"]
        for _ in range(3 + i):
            post("/api/lesson", {"enrollment_id": eid, "hours": -1, "type": "上课", "note": "记上课"})

    print("学员:", ids)
    with urllib.request.urlopen(BASE + "/api/summary", timeout=10) as r:
        print("看板:", json.dumps(json.loads(r.read().decode()), ensure_ascii=False)[:300])
    with urllib.request.urlopen(BASE + "/api/parent?name=%E6%9D%8E%E7%8E%A5%E7%90%B3&phone=2424", timeout=10) as r:
        print("家长查询:", json.dumps(json.loads(r.read().decode()), ensure_ascii=False)[:300])


if __name__ == "__main__":
    main()
