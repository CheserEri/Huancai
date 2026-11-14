import { classes, comments } from '../mock/data'

export interface Column {
  title: string
  dataIndex: string
  width?: number
  align?: 'left' | 'right' | 'center'
  money?: boolean
}

export interface PageConfig {
  tabs?: string[]
  filters?: string[]
  summary?: { label: string; value: string }[]
  primary?: string
  columns: Column[]
  rows: any[]
}

const empty: PageConfig = {
  columns: [
    { title: '名称', dataIndex: 'name' },
    { title: '状态', dataIndex: 'status', width: 120 },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180 },
  ],
  rows: [],
}

export const pageConfigs: Record<string, PageConfig> = {
  '/class': {
    tabs: ['全部班级', '开放招生', '已开班', '已结班'],
    filters: ['班级名称', '所属课程', '校区', '班级状态'],
    primary: '新建班级',
    columns: [
      { title: '班级名称', dataIndex: 'name' },
      { title: '所属课程', dataIndex: 'course' },
      { title: '校区', dataIndex: 'campus', width: 120 },
      { title: '班主任', dataIndex: 'teacher', width: 110 },
      { title: '在读/满班', dataIndex: 'count', width: 120, align: 'center' },
      { title: '上课时间', dataIndex: 'weekday', width: 180 },
      { title: '班级状态', dataIndex: 'status', width: 110 },
    ],
    rows: classes,
  },
  '/lesson': {
    tabs: ['课程列表', '课程类别', '科目设置'],
    filters: ['课程名称', '课程类别', '授课模式', '状态'],
    primary: '新建课程',
    columns: [
      { title: '课程名称', dataIndex: 'name' },
      { title: '课程类别', dataIndex: 'category', width: 140 },
      { title: '收费模式', dataIndex: 'chargeMode', width: 120 },
      { title: '课时单价', dataIndex: 'price', width: 120, align: 'right', money: true },
      { title: '标准课时', dataIndex: 'totalHours', width: 110, align: 'right' },
      { title: '开课校区', dataIndex: 'campus', width: 130 },
      { title: '状态', dataIndex: 'status', width: 100 },
    ],
    rows: [
      { name: '国学启蒙', category: '国学', chargeMode: '按课时', price: 80, totalHours: 60, campus: '鲅鱼圈', status: '启用' },
      { name: '硬笔书法', category: '书法', chargeMode: '按课时', price: 80, totalHours: 48, campus: '全校区', status: '启用' },
      { name: '少儿围棋', category: '棋类', chargeMode: '按期', price: 0, totalHours: 0, campus: '新民', status: '启用' },
      { name: '文言文阅读', category: '语文', chargeMode: '按课时', price: 90, totalHours: 24, campus: '鲅鱼圈', status: '启用' },
    ],
  },
  '/oneToOne': {
    filters: ['学员姓名', '授课教师', '课程', '状态'],
    primary: '新增一对一',
    columns: [
      { title: '学员', dataIndex: 'student' },
      { title: '课程', dataIndex: 'course' },
      { title: '授课教师', dataIndex: 'teacher', width: 120 },
      { title: '剩余课时', dataIndex: 'remain', width: 110, align: 'right' },
      { title: '到期日', dataIndex: 'expire', width: 120 },
      { title: '状态', dataIndex: 'status', width: 100 },
    ],
    rows: [
      { student: '王梓萱', course: '国学启蒙', teacher: '李老师', remain: 18, expire: '2026-12-31', status: '在读' },
      { student: '周予安', course: '少儿围棋', teacher: '赵老师', remain: 6, expire: '2026-11-30', status: '停课' },
      { student: '郑皓轩', course: '硬笔书法', teacher: '王老师', remain: 24, expire: '2027-02-20', status: '在读' },
    ],
  },
  '/bookingClass': {
    tabs: ['可约班级', '我的约课', '约课记录'],
    filters: ['班级', '课程', '约课日期', '校区'],
    columns: [
      { title: '班级名称', dataIndex: 'name' },
      { title: '课程', dataIndex: 'course' },
      { title: '可约时段', dataIndex: 'slot', width: 190 },
      { title: '剩余名额', dataIndex: 'left', width: 110, align: 'right' },
      { title: '校区', dataIndex: 'campus', width: 120 },
      { title: '状态', dataIndex: 'status', width: 100 },
    ],
    rows: [
      { name: '国学启蒙周三班', course: '国学启蒙', slot: '周三 17:30-19:00', left: 4, campus: '鲅鱼圈', status: '可约' },
      { name: '硬笔书法入门班', course: '硬笔书法', slot: '周日 10:00-11:30', left: 2, campus: '鲅鱼圈', status: '可约' },
      { name: '少儿围棋提高班', course: '少儿围棋', slot: '周六 13:30-15:00', left: 0, campus: '新民', status: '已满' },
    ],
  },
  '/teachingLog': {
    filters: ['上课日期', '班级', '课程', '教师', '记课状态'],
    summary: [
      { label: '今日待记上课', value: '17 条' },
      { label: '本周已记', value: '46 条' },
      { label: '本月课耗', value: '1,284 课时' },
    ],
    primary: '记上课',
    columns: [
      { title: '上课时间', dataIndex: 'time', width: 180 },
      { title: '班级', dataIndex: 'className' },
      { title: '课程', dataIndex: 'course', width: 130 },
      { title: '教师', dataIndex: 'teacher', width: 100 },
      { title: '应到 / 实到', dataIndex: 'attend', width: 130, align: 'center' },
      { title: '扣课时', dataIndex: 'hours', width: 110, align: 'right' },
      { title: '状态', dataIndex: 'status', width: 110 },
      { title: '记课人', dataIndex: 'operator', width: 100 },
    ],
    rows: [
      { time: '2026-10-24 17:30', className: '国学启蒙周三班', course: '国学启蒙', teacher: '李老师', attend: '16 / 15', hours: 15, status: '已记课', operator: '李老师' },
      { time: '2026-10-24 10:00', className: '硬笔书法入门班', course: '硬笔书法', teacher: '王老师', attend: '12 / 12', hours: 12, status: '已记课', operator: '王老师' },
      { time: '2026-10-23 13:30', className: '少儿围棋提高班', course: '少儿围棋', teacher: '赵老师', attend: '14 / 13', hours: 13, status: '待确认', operator: '赵老师' },
      { time: '2026-10-23 18:00', className: '文言文阅读精讲班', course: '文言文阅读', teacher: '张老师', attend: '8 / 8', hours: 8, status: '已记课', operator: '张老师' },
    ],
  },
  '/comment': {
    tabs: ['班级点评', '课堂评价', '点评统计'],
    filters: ['上课日期', '校区', '班级', '授课教师'],
    summary: [
      { label: '点评率', value: '83%' },
      { label: '阅读率', value: '78%' },
      { label: '到课人数', value: '168 人' },
      { label: '小红花', value: '426 朵' },
    ],
    primary: '批量点评',
    columns: [
      { title: '班级', dataIndex: 'className' },
      { title: '课程', dataIndex: 'course', width: 130 },
      { title: '授课教师', dataIndex: 'teacher', width: 110 },
      { title: '上课时间', dataIndex: 'time', width: 180 },
      { title: '到课 / 应到', dataIndex: 'attended', width: 130, align: 'center' },
      { title: '点评数', dataIndex: 'comments', width: 100, align: 'right' },
      { title: '阅读率', dataIndex: 'readRate', width: 100, align: 'right' },
    ],
    rows: comments,
  },
  '/homework': {
    tabs: ['作业列表', '草稿箱'],
    filters: ['布置时间', '校区', '布置教师', '班级', '是否全部点评'],
    primary: '布置作业',
    columns: [
      { title: '班级', dataIndex: 'className' },
      { title: '课程', dataIndex: 'course', width: 130 },
      { title: '布置教师', dataIndex: 'teacher', width: 110 },
      { title: '布置时间', dataIndex: 'time', width: 180 },
      { title: '点评进度', dataIndex: 'progress', width: 140 },
      { title: '未提交', dataIndex: 'unsubmit', width: 90, align: 'right' },
      { title: '未读', dataIndex: 'unread', width: 80, align: 'right' },
    ],
    rows: [
      { className: '国学启蒙周三班', course: '国学启蒙', teacher: '李老师', time: '2026-10-23 19:10', progress: '12 / 15', unsubmit: 3, unread: 2 },
      { className: '硬笔书法入门班', course: '硬笔书法', teacher: '王老师', time: '2026-10-21 11:40', progress: '12 / 12', unsubmit: 0, unread: 0 },
      { className: '少儿围棋提高班', course: '少儿围棋', teacher: '赵老师', time: '2026-10-19 15:20', progress: '8 / 13', unsubmit: 5, unread: 4 },
    ],
  },
  '/growthArchives': {
    tabs: ['全部档案', '报名档案', '课堂点评'],
    filters: ['学员姓名', '档案类型', '校区', '创建时间'],
    primary: '新建档案',
    columns: [
      { title: '学员', dataIndex: 'student' },
      { title: '档案类型', dataIndex: 'type', width: 130 },
      { title: '标题', dataIndex: 'title' },
      { title: '校区', dataIndex: 'campus', width: 120 },
      { title: '创建时间', dataIndex: 'time', width: 180 },
    ],
    rows: [
      { student: '王梓萱', type: '课堂点评', title: '《弟子规》诵读表现优秀', campus: '鲅鱼圈', time: '2026-10-23 18:02' },
      { student: '李思远', type: '报名', title: '硬笔书法 40 课时报名', campus: '鲅鱼圈', time: '2026-10-24 09:41' },
      { student: '刘芊羽', type: '课堂点评', title: '布局意识明显提升', campus: '新民', time: '2026-10-19 15:30' },
    ],
  },
  '/employee': {
    tabs: ['员工管理', '角色管理', '教师管理', '员工常见问题'],
    filters: ['所属部门', '是否教师', '管辖校区', '角色', '人事状态'],
    primary: '新建员工',
    columns: [
      { title: '姓名', dataIndex: 'name', width: 100 },
      { title: '所属部门', dataIndex: 'dept', width: 120 },
      { title: '是否教师', dataIndex: 'teacher', width: 100 },
      { title: '管辖校区', dataIndex: 'campus', width: 130 },
      { title: '角色', dataIndex: 'role', width: 130 },
      { title: '联系方式', dataIndex: 'phone', width: 140 },
      { title: '人事状态', dataIndex: 'status', width: 100 },
    ],
    rows: [
      { name: '李老师', dept: '教学部', teacher: '是', campus: '鲅鱼圈', role: '教师', phone: '186****7659', status: '在职' },
      { name: '王老师', dept: '教学部', teacher: '是', campus: '鲅鱼圈', role: '教师', phone: '139****2231', status: '在职' },
      { name: '赵老师', dept: '教学部', teacher: '是', campus: '新民', role: '教师', phone: '135****4412', status: '在职' },
      { name: '张老师', dept: '教学部', teacher: '是', campus: '鲅鱼圈', role: '教师', phone: '137****5566', status: '在职' },
      { name: '孙老师', dept: '前台', teacher: '否', campus: '鲅鱼圈', role: '前台', phone: '158****9087', status: '在职' },
      { name: '周老师', dept: '财务部', teacher: '否', campus: '全校区', role: '财务', phone: '159****7781', status: '在职' },
    ],
  },
  '/finance': {
    tabs: ['收支明细', '交易列表', '确认收入列表', '账户管理', '收支项目'],
    filters: ['订单号', '经办日期', '账户名称', '经办校区', '到款状态', '收支类型', '收支小类'],
    summary: [
      { label: '本月实收', value: '¥180,211.60' },
      { label: '本月应收', value: '¥182,363.00' },
      { label: '预收款余额', value: '¥486,920.00' },
      { label: '本月确认收入', value: '¥96,480.00' },
    ],
    primary: '新增记账',
    columns: [
      { title: '收支大类', dataIndex: 'category', width: 120 },
      { title: '收支小类', dataIndex: 'subCategory', width: 140 },
      { title: '金额', dataIndex: 'amount', width: 130, align: 'right', money: true },
      { title: '经办校区', dataIndex: 'campus', width: 110 },
      { title: '经办日期', dataIndex: 'date', width: 130 },
      { title: '经办人', dataIndex: 'operator', width: 100 },
      { title: '学员', dataIndex: 'student', width: 100 },
      { title: '到款状态', dataIndex: 'status', width: 110 },
      { title: '订单号', dataIndex: 'order', width: 180 },
    ],
    rows: [
      { category: '学费收入', subCategory: '报名/续费', amount: 3200, campus: '鲅鱼圈', date: '2026-10-24', operator: '王老师', student: '王梓萱', status: '已到账', order: 'DD202610240017' },
      { category: '学费收入', subCategory: '续费', amount: 3600, campus: '鲅鱼圈', date: '2026-10-24', operator: '李老师', student: '张一诺', status: '部分到账', order: 'DD202610240018' },
      { category: '教材费', subCategory: '教材销售', amount: 180, campus: '新民', date: '2026-10-23', operator: '赵老师', student: '郑皓轩', status: '已到账', order: 'DD202610230041' },
      { category: '退费支出', subCategory: '课程退费', amount: -320, campus: '新民', date: '2026-10-22', operator: '张老师', student: '陈子墨', status: '已到账', order: 'DD202610220015' },
      { category: '学费收入', subCategory: '余额充值', amount: 2000, campus: '新民', date: '2026-10-23', operator: '赵老师', student: '刘芊羽', status: '已到账', order: 'DD202610230029' },
    ],
  },
  '/storage': {
    tabs: ['购买记录', '库存', '教材设置', '杂费设置'],
    filters: ['订单号', '交易类型', '经办校区', '经办日期', '领用状态'],
    primary: '入库',
    columns: [
      { title: '学员', dataIndex: 'student', width: 110 },
      { title: '购买商品', dataIndex: 'product' },
      { title: '数量', dataIndex: 'qty', width: 90, align: 'right' },
      { title: '实付', dataIndex: 'paid', width: 110, align: 'right', money: true },
      { title: '领用状态', dataIndex: 'state', width: 110 },
      { title: '经办日期', dataIndex: 'date', width: 130 },
    ],
    rows: [
      { student: '郑皓轩', product: '《硬笔书法练习册》中级', qty: 1, paid: 180, state: '已领用', date: '2026-10-23' },
      { student: '王梓萱', product: '《国学经典诵读》上册', qty: 1, paid: 68, state: '待领用', date: '2026-10-24' },
      { student: '刘芊羽', product: '围棋教具套装', qty: 1, paid: 120, state: '已领用', date: '2026-10-21' },
    ],
  },
  '/reporter/teaching': {
    tabs: ['班级报表', '课时消耗', '教师工作量'],
    filters: ['统计周期', '校区', '课程'],
    summary: [
      { label: '本月课时消耗', value: '1,284 课时' },
      { label: '人均课耗', value: '0.96 课时' },
      { label: '教师课时', value: '1,128 课时' },
    ],
    columns: [
      { title: '教师', dataIndex: 'teacher', width: 120 },
      { title: '上课次数', dataIndex: 'lessons', width: 120, align: 'right' },
      { title: '课时数', dataIndex: 'hours', width: 120, align: 'right' },
      { title: '班级数', dataIndex: 'classes', width: 110, align: 'right' },
      { title: '平均到课率', dataIndex: 'rate', width: 130, align: 'right' },
    ],
    rows: [
      { teacher: '李老师', lessons: 42, hours: 63, classes: 4, rate: '94%' },
      { teacher: '王老师', lessons: 36, hours: 54, classes: 3, rate: '91%' },
      { teacher: '赵老师', lessons: 28, hours: 42, classes: 2, rate: '89%' },
      { teacher: '张老师', lessons: 16, hours: 24, classes: 2, rate: '87%' },
    ],
  },
  '/reporter/finance': {
    tabs: ['收支报表', '应收实收', '欠费'],
    filters: ['统计周期', '校区'],
    summary: [
      { label: '应收费', value: '¥182,363.00' },
      { label: '实收', value: '¥180,211.60' },
      { label: '欠费', value: '¥2,151.40' },
    ],
    columns: [
      { title: '日期', dataIndex: 'date', width: 130 },
      { title: '应收', dataIndex: 'receivable', width: 130, align: 'right', money: true },
      { title: '实收', dataIndex: 'received', width: 130, align: 'right', money: true },
      { title: '退款', dataIndex: 'refund', width: 120, align: 'right', money: true },
      { title: '欠费', dataIndex: 'debt', width: 120, align: 'right', money: true },
    ],
    rows: [
      { date: '2026-10-24', receivable: 8400, received: 6800, refund: 0, debt: 1200 },
      { date: '2026-10-23', receivable: 6320, received: 6320, refund: 0, debt: 0 },
      { date: '2026-10-22', receivable: 2160, received: 1840, refund: 320, debt: 0 },
      { date: '2026-10-21', receivable: 0, received: 0, refund: 0, debt: 0 },
    ],
  },
  '/reporter/material': {
    filters: ['统计周期', '校区', '教材'],
    summary: [{ label: '本月教材收入', value: '¥368.00' }],
    columns: [
      { title: '教材名称', dataIndex: 'name' },
      { title: '售出数量', dataIndex: 'qty', width: 120, align: 'right' },
      { title: '收入金额', dataIndex: 'amount', width: 130, align: 'right', money: true },
      { title: '剩余库存', dataIndex: 'stock', width: 120, align: 'right' },
    ],
    rows: [
      { name: '《硬笔书法练习册》中级', qty: 1, amount: 180, stock: 24 },
      { name: '《国学经典诵读》上册', qty: 1, amount: 68, stock: 36 },
      { name: '围棋教具套装', qty: 1, amount: 120, stock: 8 },
    ],
  },
  '/reporter/screen': {
    columns: [],
    rows: [],
  },
  '/reporter/ai': {
    columns: [],
    rows: [],
  },
}

export function getPageConfig(path: string): PageConfig {
  return pageConfigs[path] ?? empty
}
