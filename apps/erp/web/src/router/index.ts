import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { menuEntries, menuIndex } from '../config/menu'
import { hasMenuPermission, HOME_PATH, isAuthed } from '../stores/auth'
import MainLayout from '../layout/MainLayout.vue'
import ModulePage from '../views/ModulePage.vue'

const specialViews: Record<string, () => Promise<unknown>> = {
  '/desktop': () => import('../views/Dashboard.vue'),
  '/student': () => import('../views/Students.vue'),
  '/order': () => import('../views/Orders.vue'),
  '/priceTemplate': () => import('../views/PriceTemplates.vue'),
  '/teacherHours': () => import('../views/TeacherHours.vue'),
  '/schedule': () => import('../views/Schedule.vue'),
  '/comment': () => import('../views/Comments.vue'),
  '/class': () => import('../views/Classes.vue'),
  '/lesson': () => import('../views/Courses.vue'),
  '/teachingLog': () => import('../views/TeachingLog.vue'),
  '/employee': () => import('../views/Staff.vue'),
  '/orgStructure': () => import('../views/OrgStructure.vue'),
  '/finance': () => import('../views/Finance.vue'),
  '/reporter/business': () => import('../views/ReportBusiness.vue'),
  '/reporter/teaching': () => import('../views/ReportTeaching.vue'),
  '/reporter/finance': () => import('../views/ReportFinance.vue'),
  '/reporter/material': () => import('../views/ReportMaterial.vue'),
  '/reporter/screen': () => import('../views/ReportScreen.vue'),
  '/reporter/ai': () => import('../views/ReportAi.vue'),
  '/oneToOne': () => import('../views/OneToOne.vue'),
  '/bookingClass': () => import('../views/BookingClass.vue'),
  '/homework': () => import('../views/Homework.vue'),
  '/growthArchives': () => import('../views/GrowthArchives.vue'),
  '/storage': () => import('../views/Storage.vue'),
  '/pointsMall': () => import('../views/PointsMall.vue'),
  '/dataFeeTemplate': () => import('../views/DataFeeTemplates.vue'),
  '/marketingSolution': () => import('../views/MarketingSolution.vue'),
  '/gradeExam': () => import('../views/GradeExams.vue'),
  '/gradeEntry': () => import('../views/GradeEntry.vue'),
  '/gradeQuery': () => import('../views/GradeQuery.vue'),
  '/gradeAnalysis': () => import('../views/GradeAnalysis.vue'),
  '/messagePush': () => import('../views/MessagePush.vue'),
  '/classSuspend': () => import('../views/ClassSuspend.vue'),
  '/classComment': () => import('../views/ClassComment.vue'),
  '/classHomework': () => import('../views/ClassHomework.vue'),
  '/classAlbum': () => import('../views/ClassAlbum.vue'),
}

const children: RouteRecordRaw[] = menuEntries.map((item) => ({
  path: item.path,
  component: specialViews[item.path] ?? ModulePage,
  meta: {
    title: item.title,
    status: item.status,
    source: item.source,
    group: item.groupTitle,
  },
}))

// 班级详情为二级页面，不进入左侧菜单，但仍需登录后访问
children.push({
  path: '/classDetail/:id',
  component: () => import('../views/ClassDetail.vue'),
  meta: { title: '班级详情', group: '教务中心' },
})

// 班级升班为二级页面，从班级列表「升班」进入，不进入左侧菜单
children.push({
  path: '/classUpgrade',
  component: () => import('../views/ClassUpgrade.vue'),
  meta: { title: '班级升班', group: '教务中心' },
})

// 学员详情为二级页面，点击学员姓名进入，不进入左侧菜单
children.push({
  path: '/studentDetail/:id',
  component: () => import('../views/StudentDetail.vue'),
  meta: { title: '学员详情', group: '教务中心' },
})

// 新建报名 / 续费办理页，从学员详情或订单列表进入，不进入左侧菜单
children.push({
  path: '/enroll',
  component: () => import('../views/EnrollOrder.vue'),
  meta: { title: '新建报名', group: '办理中心' },
})

// 新建转课办理页，从学员详情或订单列表进入，不进入左侧菜单
children.push({
  path: '/transfer',
  component: () => import('../views/TransferOrder.vue'),
  meta: { title: '新建转课', group: '办理中心' },
})

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      component: () => import('../views/Login.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/',
      component: MainLayout,
      redirect: '/desktop',
      children,
    },
    // 已删除或不存在的页面路径统一回到工作台，避免出现空白页
    { path: '/:pathMatch(.*)*', redirect: HOME_PATH },
  ],
})

router.beforeEach((to) => {
  const authed = isAuthed()
  if (to.path === '/login') {
    return authed ? HOME_PATH : true
  }
  if (!authed) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 已登录但没有该菜单权限时，统一回到工作台，避免直接输网址越权访问
  if (menuIndex.has(to.path) && !hasMenuPermission(to.path)) {
    return HOME_PATH
  }
  return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || '工作台'
  document.title = `${title} · 幻彩教育管理系统`
})

export default router
