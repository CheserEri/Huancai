const api = require('../../utils/api')
const util = require('../../utils/util')

const app = getApp()

Page({
  data: {
    loading: true,
    error: '',
    parent: null,
    children: [],
    version: '1.0.0'
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
      .then((data) => {
        app.globalData.profile = data.parent
        app.setChildren(data.children || [])
        this.setData({
          loading: false,
          parent: {
            ...data.parent,
            nickname: data.parent.nickname || '微信家长',
            initial: (data.parent.nickname || '家').slice(0, 1)
          },
          children: (data.children || []).map((item) => ({
            ...item,
            initial: (item.name || '学').slice(0, 1),
            remainText: util.lesson(item.remainLessons)
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

  viewChild(event) {
    const studentId = Number(event.currentTarget.dataset.id)
    app.setChild(studentId)
    wx.switchTab({ url: '/pages/lessons/lessons' })
  },

  unbind(event) {
    const studentId = Number(event.currentTarget.dataset.id)
    const child = this.data.children.find((item) => item.studentId === studentId)
    wx.showModal({
      title: '解除绑定',
      content: '确定解除与「' + (child ? child.name : '该学员') + '」的绑定吗？',
      confirmColor: '#c81e2b',
      success: (res) => {
        if (!res.confirm) return
        api.request('/unbind?studentId=' + studentId, { method: 'POST' })
          .then((data) => {
            app.globalData.profile = data.parent
            app.setChildren(data.children || [])
            this.load()
            wx.showToast({ title: '已解除绑定', icon: 'none' })
          })
          .catch((err) => {
            wx.showToast({ title: err.message || '操作失败', icon: 'none' })
          })
      }
    })
  },

  goBind() {
    wx.navigateTo({ url: '/pages/bind/bind' })
  },

  goComments(event) {
    const studentId = Number(event.currentTarget.dataset.id)
    if (!studentId) return
    wx.navigateTo({ url: '/pages/comments/comments?studentId=' + studentId })
  },

  goTab(event) {
    wx.switchTab({ url: event.currentTarget.dataset.url })
  },

  goMessages() {
    wx.navigateTo({ url: '/pages/messages/messages' })
  }
})
