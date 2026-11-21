export type FeatureStatus = 'ok' | 'locked' | 'external'

export interface MenuItem {
  path: string
  title: string
  status: FeatureStatus
  /** 原始路由，仅作对照 */
  source?: string
  icon?: string
}

export interface MenuGroup {
  key: string
  title: string
  icon: string
  items: MenuItem[]
}

export interface MenuEntry extends MenuItem {
  group: string
  groupTitle: string
}

export const menuGroups: MenuGroup[] = [
  {
    key: 'home',
    title: '工作台',
    icon: 'AppstoreOutlined',
    items: [{ path: '/desktop', title: '工作台', status: 'ok', icon: 'AppstoreOutlined', source: '/web/desktop' }],
  },
  {
    key: 'recruit',
    // 招生中心：只保留活动效果统计，活动模板统一在「办理中心 - 缴费模板」维护。
    title: '招生中心',
    icon: 'RocketOutlined',
    items: [
      { path: '/marketingSolution', title: '活动效果', status: 'ok', source: '/web/MarketingSolution/Index' },
    ],
  },
  {
    key: 'handle',
    title: '办理中心',
    icon: 'AccountBookOutlined',
    items: [
      { path: '/order', title: '订单列表', status: 'ok', icon: 'AccountBookOutlined', source: '/web/order/orderList' },
      { path: '/priceTemplate', title: '缴费模板', status: 'ok', icon: 'TagsOutlined' },
      { path: '/teacherHours', title: '教师课时', status: 'ok', icon: 'FieldTimeOutlined' },
    ],
  },
  {
    key: 'academic',
    title: '教务中心',
    icon: 'TeamOutlined',
    items: [
      { path: '/student', title: '学员', status: 'ok', icon: 'SmileOutlined', source: '/web/student' },
      { path: '/class', title: '班级', status: 'ok', source: '/web/class' },
      { path: '/schedule', title: '排课', status: 'ok', icon: 'CalendarOutlined', source: '/web/schedule' },
      { path: '/teachingLog', title: '记上课', status: 'ok', icon: 'EditOutlined', source: '/web/teachingLog' },
      { path: '/attendance', title: '磁卡考勤', status: 'locked', source: '/web/Reception/AttendanceIndex' },
      { path: '/lesson', title: '课程模板', status: 'ok', source: '/web/Lesson' },
    ],
  },
  {
    key: 'grade',
    title: '成绩中心',
    icon: 'BarChartOutlined',
    items: [
      { path: '/gradeExam', title: '考试管理', status: 'ok', icon: 'FormOutlined' },
      { path: '/gradeEntry', title: '成绩录入', status: 'ok', icon: 'EditOutlined' },
      { path: '/gradeQuery', title: '成绩查询', status: 'ok', icon: 'SearchOutlined' },
      { path: '/gradeAnalysis', title: '成绩分析', status: 'ok', icon: 'BarChartOutlined' },
    ],
  },
  {
    key: 'family',
    title: '家校服务',
    icon: 'NotificationOutlined',
    items: [
      { path: '/messagePush', title: '消息推送', status: 'ok', icon: 'SendOutlined' },
      { path: '/activityPoster', title: '活动海报推送', status: 'ok', icon: 'PictureOutlined' },
      { path: '/classSuspend', title: '班级休课', status: 'ok', icon: 'PauseCircleOutlined' },
      { path: '/classComment', title: '课堂点评', status: 'ok', icon: 'CommentOutlined' },
      { path: '/classHomework', title: '作业', status: 'ok', icon: 'BookOutlined' },
      { path: '/classAlbum', title: '班级相册', status: 'ok', icon: 'PictureOutlined' },
    ],
  },
  {
    key: 'admin',
    title: '内部管理',
    icon: 'SettingOutlined',
    items: [
      { path: '/orgStructure', title: '组织架构', status: 'ok', source: '/web/Settings/Index#/architecture' },
      { path: '/employee', title: '员工', status: 'ok', source: '/web/employee/employeeList' },
      { path: '/finance', title: '财务', status: 'ok', icon: 'MoneyCollectOutlined', source: '/web/finance' },
      { path: '/storage', title: '教材', status: 'ok', source: '/web/storage' },
      { path: '/pointsMall', title: '积分兑换', status: 'ok' },
      { path: '/dataFeeTemplate', title: '资料费模板', status: 'ok' },
    ],
  },
]

/** 操作权限：不显示在侧边栏，仅在员工 / 权限模板的权限配置中勾选。 */
export const actionPermissionGroups: MenuGroup[] = [
  {
    key: 'action',
    title: '操作权限',
    icon: 'SafetyCertificateOutlined',
    items: [
      { path: '/student/refund-edit', title: '修改退费课时/金额', status: 'ok' },
      { path: '/order/edit', title: '修改订单', status: 'ok' },
      { path: '/order/edit-amount', title: '修改订单金额', status: 'ok' },
      { path: '/order/edit-lessons', title: '修改订单课时', status: 'ok' },
    ],
  },
]

export const menuEntries: MenuEntry[] = menuGroups.flatMap((group) =>
  group.items.map((item) => ({ ...item, group: group.key, groupTitle: group.title })),
)

export const menuIndex = new Map(menuEntries.map((entry) => [entry.path, entry]))

export const statusText: Record<FeatureStatus, string> = {
  ok: '可用',
  locked: '无权限',
  external: '外部服务',
}
