export interface StudentRow {
  id: number
  name: string
  gender: string
  phone: string
  campus: string
  course: string
  remain: number
  expire: string
  status: string
  debt: number
  source: string
  createdAt: string
}

export const students: StudentRow[] = [
  { id: 1, name: '王梓萱', gender: '女', phone: '186****7659', campus: '鲅鱼圈幻彩国学堂', course: '国学启蒙', remain: 42, expire: '2026-12-31', status: '在读', debt: 0, source: '转介绍', createdAt: '2025-09-01' },
  { id: 2, name: '李思远', gender: '男', phone: '139****2231', campus: '鲅鱼圈幻彩国学堂', course: '硬笔书法', remain: 18.5, expire: '2026-11-20', status: '在读', debt: 0, source: '地推', createdAt: '2025-10-12' },
  { id: 3, name: '张一诺', gender: '女', phone: '158****9087', campus: '鲅鱼圈幻彩国学堂', course: '国学启蒙', remain: 7, expire: '2026-11-05', status: '在读', debt: 1200, source: '美团', createdAt: '2025-11-08' },
  { id: 4, name: '刘芊羽', gender: '女', phone: '135****4412', campus: '新民幻彩国学堂', course: '少儿围棋', remain: 60, expire: '2027-03-01', status: '在读', debt: 0, source: '老生续费', createdAt: '2024-03-15' },
  { id: 5, name: '陈子墨', gender: '男', phone: '186****3378', campus: '新民幻彩国学堂', course: '文言文阅读', remain: 0, expire: '2026-10-10', status: '结业', debt: 0, source: '转介绍', createdAt: '2024-09-20' },
  { id: 6, name: '赵沐阳', gender: '男', phone: '137****5566', campus: '鲅鱼圈幻彩国学堂', course: '硬笔书法', remain: 24, expire: '2027-01-15', status: '在读', debt: 0, source: '自然到访', createdAt: '2026-02-11' },
  { id: 7, name: '孙可欣', gender: '女', phone: '159****7781', campus: '鲅鱼圈幻彩国学堂', course: '国学启蒙', remain: 12, expire: '2026-11-18', status: '在读', debt: 480, source: '抖音', createdAt: '2026-03-02' },
  { id: 8, name: '周予安', gender: '男', phone: '188****2043', campus: '新民幻彩国学堂', course: '少儿围棋', remain: 33, expire: '2026-12-09', status: '停课', debt: 0, source: '转介绍', createdAt: '2025-06-18' },
  { id: 9, name: '吴雨桐', gender: '女', phone: '131****6620', campus: '鲅鱼圈幻彩国学堂', course: '文言文阅读', remain: 9, expire: '2026-11-02', status: '在读', debt: 0, source: '招生表单', createdAt: '2026-04-07' },
  { id: 10, name: '郑皓轩', gender: '男', phone: '176****8894', campus: '新民幻彩国学堂', course: '硬笔书法', remain: 48, expire: '2027-02-20', status: '在读', debt: 0, source: '自然到访', createdAt: '2025-12-01' },
  { id: 11, name: '林书瑶', gender: '女', phone: '185****1172', campus: '鲅鱼圈幻彩国学堂', course: '国学启蒙', remain: 2, expire: '2026-10-31', status: '在读', debt: 0, source: '转介绍', createdAt: '2025-05-09' },
  { id: 12, name: '高晟睿', gender: '男', phone: '133****4405', campus: '新民幻彩国学堂', course: '少儿围棋', remain: 15, expire: '2026-12-25', status: '休学', debt: 0, source: '地推', createdAt: '2025-08-22' },
]

export interface OrderRow {
  id: string
  student: string
  phone: string
  type: string
  content: string
  receivable: number
  received: number
  discount: number
  debt: number
  campus: string
  handler: string
  date: string
  status: string
  method: string
}

export const orders: OrderRow[] = [
  { id: 'DD202610240018', student: '张一诺', phone: '158****9087', type: '续费', content: '国学启蒙 · 60 课时', receivable: 4800, received: 3600, discount: 200, debt: 1200, campus: '鲅鱼圈', handler: '李老师', date: '2026-10-24 10:22', status: '待支付', method: '微信' },
  { id: 'DD202610240017', student: '王梓萱', phone: '186****7659', type: '报名', content: '硬笔书法 · 40 课时', receivable: 3200, received: 3200, discount: 0, debt: 0, campus: '鲅鱼圈', handler: '王老师', date: '2026-10-24 09:41', status: '已完成', method: '现金' },
  { id: 'DD202610230052', student: '孙可欣', phone: '159****7781', type: '补费', content: '国学启蒙 · 差额补缴', receivable: 480, received: 480, discount: 0, debt: 0, campus: '鲅鱼圈', handler: '李老师', date: '2026-10-23 17:08', status: '已完成', method: '支付宝' },
  { id: 'DD202610230041', student: '郑皓轩', phone: '176****8894', type: '报名', content: '硬笔书法 · 48 课时', receivable: 3840, received: 3840, discount: 160, debt: 0, campus: '新民', handler: '赵老师', date: '2026-10-23 15:30', status: '已完成', method: '银行卡' },
  { id: 'DD202610230029', student: '刘芊羽', phone: '135****4412', type: '余额充值', content: '学员账户充值', receivable: 2000, received: 2000, discount: 0, debt: 0, campus: '新民', handler: '赵老师', date: '2026-10-23 11:12', status: '已完成', method: '微信' },
  { id: 'DD202610220015', student: '陈子墨', phone: '186****3378', type: '退费', content: '文言文阅读 · 剩余 0 课时', receivable: 0, received: -320, discount: 0, debt: 0, campus: '新民', handler: '张老师', date: '2026-10-22 16:45', status: '已完成', method: '现金' },
  { id: 'DD202610220008', student: '吴雨桐', phone: '131****6620', type: '报名', content: '文言文阅读 · 24 课时', receivable: 2160, received: 2160, discount: 0, debt: 0, campus: '鲅鱼圈', handler: '王老师', date: '2026-10-22 10:05', status: '已完成', method: '微信' },
  { id: 'DD202610210033', student: '林书瑶', phone: '185****1172', type: '转课', content: '国学启蒙 → 少儿围棋', receivable: 0, received: 0, discount: 0, debt: 0, campus: '鲅鱼圈', handler: '李老师', date: '2026-10-21 14:20', status: '已完成', method: '—' },
]

export interface ClassRow {
  id: number
  name: string
  course: string
  campus: string
  teacher: string
  count: number
  capacity: number
  status: string
  weekday: string
}

export const classes: ClassRow[] = [
  { id: 1, name: '国学启蒙周三班', course: '国学启蒙', campus: '鲅鱼圈', teacher: '李老师', count: 16, capacity: 20, status: '已开班', weekday: '周三 17:30-19:00' },
  { id: 2, name: '国学启蒙周六班', course: '国学启蒙', campus: '鲅鱼圈', teacher: '李老师', count: 19, capacity: 20, status: '满班', weekday: '周六 09:00-10:30' },
  { id: 3, name: '硬笔书法入门班', course: '硬笔书法', campus: '鲅鱼圈', teacher: '王老师', count: 12, capacity: 16, status: '已开班', weekday: '周日 10:00-11:30' },
  { id: 4, name: '少儿围棋提高班', course: '少儿围棋', campus: '新民', teacher: '赵老师', count: 14, capacity: 16, status: '已开班', weekday: '周六 13:30-15:00' },
  { id: 5, name: '文言文阅读精讲班', course: '文言文阅读', campus: '鲅鱼圈', teacher: '张老师', count: 8, capacity: 18, status: '开放招生', weekday: '周五 18:00-19:30' },
  { id: 6, name: '硬笔书法进阶班', course: '硬笔书法', campus: '新民', teacher: '王老师', count: 15, capacity: 16, status: '已开班', weekday: '周四 17:00-18:30' },
]

export interface CommentRow {
  id: number
  className: string
  course: string
  teacher: string
  time: string
  attended: number
  total: number
  comments: number
  readRate: string
}

export const comments: CommentRow[] = [
  { id: 1, className: '国学启蒙周三班', course: '国学启蒙', teacher: '李老师', time: '2026-10-23 17:30', attended: 15, total: 16, comments: 15, readRate: '86%' },
  { id: 2, className: '硬笔书法入门班', course: '硬笔书法', teacher: '王老师', time: '2026-10-21 10:00', attended: 12, total: 12, comments: 12, readRate: '92%' },
  { id: 3, className: '少儿围棋提高班', course: '少儿围棋', teacher: '赵老师', time: '2026-10-19 13:30', attended: 13, total: 14, comments: 10, readRate: '71%' },
  { id: 4, className: '文言文阅读精讲班', course: '文言文阅读', teacher: '张老师', time: '2026-10-18 18:00', attended: 8, total: 8, comments: 8, readRate: '88%' },
]
