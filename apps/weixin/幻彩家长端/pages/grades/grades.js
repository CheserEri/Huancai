const api = require('../../utils/api')
const util = require('../../utils/util')
const config = require('../../config')

const app = getApp()
// 后端试卷图片是 /uploads/... 相对路径，小程序需拼上服务地址
const FILE_BASE = config.baseUrl.replace(/\/api\/wxapp\/?$/, '')

Page({
  data: {
    loading: true,
    error: '',
    noChild: false,
    child: null,
    stats: null,
    trend: [],
    records: []
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
        return api.request('/grades?studentId=' + childId)
      })
      .then((list) => {
        if (!list) return
        const records = (list || []).map((item) => {
          const full = Number(item.fullScore || 100)
          const score = Number(item.totalScore)
          return {
            ...item,
            scoreText: util.score(item.totalScore),
            fullText: util.score(item.fullScore),
            deltaText: util.deltaText(item.deltaScore),
            deltaClass: util.deltaClass(item.deltaScore),
            rankText: util.rank(item.rankClass),
            dateText: util.shortDate(item.examDate),
            paperImage: item.paperImage ? FILE_BASE + item.paperImage : '',
            percent: Number.isNaN(score) || !full ? 0 : Math.min(100, Math.round((score / full) * 100))
          }
        })
        const scored = records.filter((item) => item.totalScore !== null && item.totalScore !== undefined)
        const values = scored.map((item) => Number(item.totalScore))
        const best = values.length ? Math.max.apply(null, values) : null
        const latest = scored[0] || null
        const trendSource = scored.slice(0, 8).reverse()
        this.setData({
          loading: false,
          noChild: false,
          records,
          stats: records.length
            ? {
                latestText: latest ? latest.scoreText : '—',
                latestDelta: latest ? latest.deltaText : '',
                latestDeltaClass: latest ? latest.deltaClass : 'flat',
                bestText: best === null ? '—' : util.score(best),
                count: records.length
              }
            : null,
          trend: trendSource.map((item) => ({
            key: item.examId,
            label: item.examDate ? String(item.examDate).slice(5, 10).replace('-', '/') : '',
            scoreText: item.scoreText,
            barHeight: Math.max(18, Math.round(item.percent * 1.5)),
            barClass: item.percent >= 85 ? 'high' : (item.percent >= 60 ? 'mid' : 'low')
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

  previewPaper(e) {
    const src = e.currentTarget.dataset.src
    if (!src) return
    wx.previewImage({ current: src, urls: [src] })
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
  }
})
