const api = require('../../utils/api')
const util = require('../../utils/util')

Page({
  data: {
    loading: true,
    error: '',
    messages: [],
    unread: 0
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
      .then(() => api.request('/messages'))
      .then((list) => {
        const messages = (list || []).map((item) => ({
          ...item,
          dateText: util.dateTime(item.sentAt),
          expanded: false
        }))
        this.setData({
          loading: false,
          messages,
          unread: messages.filter((item) => !item.readFlag).length
        })
      })
      .catch((err) => {
        this.setData({ loading: false, error: err.message || '加载消息失败' })
      })
      .finally(() => {
        wx.stopPullDownRefresh()
      })
  },

  toggle(event) {
    const id = Number(event.currentTarget.dataset.id)
    const current = this.data.messages.find((item) => item.id === id)
    if (!current) return
    // 活动海报消息：标记已读后跳转活动页并定位到该海报
    if (current.msgType === '活动海报' && current.posterId) {
      this.markRead(id)
      wx.setStorageSync('poster_focus_id', current.posterId)
      wx.switchTab({ url: '/pages/activities/activities' })
      return
    }
    const expanded = !current.expanded
    const messages = this.data.messages.map((item) => (item.id === id ? { ...item, expanded } : item))
    this.setData({ messages })

    if (!current.readFlag) {
      this.markRead(id)
    }
  },

  markRead(id) {
    api.request('/messages/' + id + '/read', { method: 'POST' })
      .then(() => {
        const readMessages = this.data.messages.map((item) =>
          item.id === id ? { ...item, readFlag: 1, readAt: new Date().toISOString() } : item
        )
        this.setData({
          messages: readMessages,
          unread: readMessages.filter((item) => !item.readFlag).length
        })
      })
      .catch(() => {
        // 标记已读失败不打断阅读，下次进入页面会重新同步
      })
  },

  retry() {
    this.load()
  }
})
