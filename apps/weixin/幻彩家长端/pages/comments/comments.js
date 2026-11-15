const api = require('../../utils/api')
const util = require('../../utils/util')

const app = getApp()

Page({
  data: {
    studentId: null,
    childName: '',
    loading: true,
    loadingMore: false,
    error: '',
    list: [],
    page: 1,
    hasMore: false,
    total: 0,
    unread: 0
  },

  onLoad(options) {
    const studentId = Number(options.studentId) || app.globalData.childId
    const child = app.globalData.children.find((item) => item.studentId === studentId)
    this.setData({
      studentId,
      childName: child ? child.name : ''
    })
    this.load(1)
  },

  onPullDownRefresh() {
    this.load(1)
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loadingMore) {
      this.load(this.data.page + 1)
    }
  },

  retry() {
    this.load(1)
  },

  load(page) {
    if (!this.data.studentId) {
      this.setData({ loading: false, error: '请先绑定孩子' })
      return Promise.resolve()
    }
    this.setData(page === 1 ? { loading: true, error: '' } : { loadingMore: true })
    return api.request('/comments?studentId=' + this.data.studentId + '&page=' + page)
      .then((data) => {
        const items = (data.list || []).map((item) => this.decorate(item))
        this.setData({
          loading: false,
          loadingMore: false,
          list: page === 1 ? items : this.data.list.concat(items),
          page: data.page,
          hasMore: data.hasMore,
          total: data.total,
          unread: data.unread,
          childName: this.data.childName || (app.currentChild() || {}).name || ''
        })
      })
      .catch((err) => {
        this.setData({ loading: false, loadingMore: false, error: err.message || '加载失败' })
      })
      .finally(() => {
        wx.stopPullDownRefresh()
      })
  },

  decorate(item) {
    const rating = item.rating || ''
    let ratingClass = ''
    if (rating.indexOf('优') >= 0) ratingClass = 'tag-green'
    else if (rating.indexOf('待') >= 0 || rating.indexOf('需') >= 0) ratingClass = 'tag-orange'
    return {
      ...item,
      ratingClass,
      dateText: util.shortDate(item.lessonDate || item.createdAt),
      isNew: !item.readAt
    }
  },

  markRead(event) {
    const index = event.currentTarget.dataset.index
    const item = this.data.list[index]
    if (!item || item.readAt) return
    api.request('/comments/' + item.id + '/read?studentId=' + this.data.studentId, { method: 'POST' })
      .then(() => {
        const list = this.data.list.slice()
        list[index] = { ...list[index], readAt: new Date().toISOString(), isNew: false }
        this.setData({
          list,
          unread: Math.max(0, this.data.unread - 1)
        })
      })
      .catch(() => {})
  }
})
