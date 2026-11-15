const api = require('../../utils/api')
const util = require('../../utils/util')

const app = getApp()

Page({
  data: {
    loading: true,
    error: '',
    noChild: false,
    greeting: '',
    children: [],
    childId: null,
    home: null,
    courses: [],
    upcoming: [],
    activities: [],
    latestComment: null,
    latestGrade: null,
    stat: null,
    messageUnread: 0
  },

  onShow() {
    this.setData({ greeting: this.greeting() })
    this.load()
    this.loadUnread()
  },

  onPullDownRefresh() {
    this.load(true)
  },

  retry() {
    this.load()
  },

  greeting() {
    const hour = new Date().getHours()
    if (hour < 11) return '早上好'
    if (hour < 18) return '下午好'
    return '晚上好'
  },

  load(fromPull) {
    if (!fromPull) {
      this.setData({ loading: true, error: '' })
    }
    return api.autoLogin()
      .then((auth) => {
        app.setChildren(auth.children || [])
        return app.globalData.children
      })
      .then((children) => {
        if (!children.length) {
          this.setData({ loading: false, noChild: true, children: [], home: null })
          return null
        }
        let childId = app.globalData.childId
        if (!children.some((item) => item.studentId === childId)) {
          childId = children[0].studentId
        }
        app.setChild(childId)
        this.setData({
          children: children.map((item) => ({
            ...item,
            initial: (item.name || '学').slice(0, 1)
          })),
          childId
        })
        return api.request('/home?studentId=' + childId)
      })
      .then((home) => {
        if (!home) return
        const courseList = home.courses || []
        const courseTotal = courseList.reduce((sum, item) => sum + Number(item.totalLessons || 0) + Number(item.giftLessons || 0), 0)
        const courseUsed = courseList.reduce((sum, item) => sum + Number(item.usedLessons || 0), 0)
        this.setData({
          loading: false,
          noChild: false,
          home,
          courses: (home.courses || []).map((course) => {
            const total = Number(course.totalLessons || 0) + Number(course.giftLessons || 0)
            const used = Number(course.usedLessons || 0)
            return {
              ...course,
              remainText: util.lesson(course.remainLessons),
              totalText: util.lesson(total),
              usedText: util.lesson(used),
              percent: total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0
            }
          }),
          upcoming: (home.upcoming || []).map((lesson) => ({
            ...lesson,
            dateText: util.shortDate(lesson.lessonDate),
            timeText: util.timeRange(lesson.startTime, lesson.endTime)
          })),
          activities: (home.activities || []).map((item) => ({
            ...item,
            ruleTexts: (item.rules || []).map((rule) => util.ruleText(rule)),
            dateText: item.startDate
              ? (item.endDate ? util.shortDate(item.startDate) + ' - ' + util.shortDate(item.endDate) : util.shortDate(item.startDate) + ' 起')
              : ''
          })),
          latestComment: home.latestComment
            ? {
                ...home.latestComment,
                dateText: util.shortDate(home.latestComment.lessonDate || home.latestComment.createdAt),
                isNew: !home.latestComment.readAt
              }
            : null,
          latestGrade: home.latestGrade
            ? {
                ...home.latestGrade,
                scoreText: util.score(home.latestGrade.totalScore),
                deltaText: util.deltaText(home.latestGrade.deltaScore),
                deltaClass: util.deltaClass(home.latestGrade.deltaScore),
                rankText: util.rank(home.latestGrade.rankClass),
                dateText: util.shortDate(home.latestGrade.examDate)
              }
            : null,
          stat: {
            remainText: util.lesson(home.totalRemainLessons),
            courseCount: (home.courses || []).length,
            upcomingCount: (home.upcoming || []).length,
            unread: home.unreadComments || 0,
            usedPercent: courseTotal > 0 ? Math.min(100, Math.round((courseUsed / courseTotal) * 100)) : 0
          }
        })
      })
      .catch((err) => {
        this.setData({ loading: false, error: err.message || '加载失败' })
      })
      .finally(() => {
        wx.stopPullDownRefresh()
      })
  },

  switchChild(event) {
    const studentId = Number(event.currentTarget.dataset.id)
    if (studentId === this.data.childId) return
    app.setChild(studentId)
    this.setData({ childId: studentId })
    this.load()
  },

  goLessons() {
    wx.switchTab({ url: '/pages/lessons/lessons' })
  },

  goGrades() {
    wx.switchTab({ url: '/pages/grades/grades' })
  },

  goActivities() {
    wx.switchTab({ url: '/pages/activities/activities' })
  },

  goComments() {
    if (!this.data.childId) return
    wx.navigateTo({ url: '/pages/comments/comments?studentId=' + this.data.childId })
  },

  goBind() {
    wx.navigateTo({ url: '/pages/bind/bind' })
  },

  loadUnread() {
    api.request('/messages/unread-count')
      .then((count) => {
        this.setData({ messageUnread: Number(count) || 0 })
      })
      .catch(() => {})
  },

  goMessages() {
    wx.navigateTo({ url: '/pages/messages/messages' })
  }
})
