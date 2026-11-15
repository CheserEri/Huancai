const api = require('../../utils/api')
const util = require('../../utils/util')

const app = getApp()

Page({
  data: {
    loading: true,
    error: '',
    noChild: false,
    child: null,
    summary: null,
    courses: [],
    txns: []
  },

  onShow() {
    this.load()
  },

  onPullDownRefresh() {
    this.load()
  },

  load() {
    this.setData({ loading: true, error: '' })
    return api.autoLogin()
      .then((auth) => {
        app.setChildren(auth.children || [])
        const children = app.globalData.children
        if (!children.length) {
          this.setData({ loading: false, noChild: true, child: null })
          return null
        }
        let childId = app.globalData.childId
        if (!children.some((item) => item.studentId === childId)) {
          childId = children[0].studentId
          app.setChild(childId)
        }
        const child = children.find((item) => item.studentId === childId)
        this.setData({ child: child ? { ...child, initial: (child.name || '学').slice(0, 1) } : null })
        return api.request('/lessons?studentId=' + childId)
      })
      .then((data) => {
        if (!data) return
        this.setData({
          loading: false,
          noChild: false,
          summary: {
            totalText: util.lesson(data.summary.totalLessons),
            usedText: util.lesson(data.summary.usedLessons),
            remainText: util.lesson(data.summary.remainLessons),
            courseCount: data.summary.courses,
            usedPercent: Number(data.summary.totalLessons) > 0
              ? Math.min(100, Math.round((Number(data.summary.usedLessons) / Number(data.summary.totalLessons)) * 100))
              : 0
          },
          courses: (data.courses || []).map((course) => {
            const total = Number(course.totalLessons || 0) + Number(course.giftLessons || 0)
            const used = Number(course.usedLessons || 0)
            return {
              ...course,
              remainText: util.lesson(course.remainLessons),
              totalText: util.lesson(total),
              usedText: util.lesson(used),
              giftText: util.lesson(course.giftLessons),
              percent: total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0,
              expireText: course.expireDate ? '有效期至 ' + course.expireDate : '长期有效'
            }
          }),
          txns: (data.recentTxns || []).map((item) => ({
            ...item,
            dateText: util.dateTime(item.createdAt),
            lessonsText: (Number(item.lessons) > 0 ? '+' : '') + util.lesson(item.lessons),
            positive: Number(item.lessons) > 0,
            balanceText: util.lesson(item.balanceAfter)
          }))
        })
      })
      .catch((err) => {
        this.setData({ loading: false, error: err.message || '加载失败' })
      })
      .finally(() => {
        wx.stopPullDownRefresh()
      })
  },

  switchChild() {
    const children = app.globalData.children || []
    if (children.length < 2) return
    wx.showActionSheet({
      itemList: children.map((item) => item.name),
      success: (res) => {
        const target = children[res.tapIndex]
        if (target) {
          app.setChild(target.studentId)
          this.load()
        }
      }
    })
  },

  goBind() {
    wx.navigateTo({ url: '/pages/bind/bind' })
  },

  goAlbum(event) {
    const classId = event.currentTarget.dataset.classId
    if (!classId) return
    wx.navigateTo({ url: '/pages/album/album?classId=' + classId })
  }
})
