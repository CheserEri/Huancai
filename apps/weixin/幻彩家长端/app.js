const api = require('./utils/api')

App({
  globalData: {
    profile: null,
    children: [],
    childId: null
  },

  onLaunch() {
    this.globalData.childId = wx.getStorageSync('wxapp_child_id') || null
    this.globalData.children = wx.getStorageSync('wxapp_children') || []
    // 启动即静默登录，页面请求时无需再等待
    this.ready = api.autoLogin().catch(() => null)
  },

  setChildren(children) {
    this.globalData.children = children || []
    wx.setStorageSync('wxapp_children', this.globalData.children)
    if ((!this.globalData.childId || !this.globalData.children.some((item) => item.studentId === this.globalData.childId))
      && this.globalData.children.length) {
      this.setChild(this.globalData.children[0].studentId)
    }
  },

  setChild(studentId) {
    this.globalData.childId = studentId
    wx.setStorageSync('wxapp_child_id', studentId)
  },

  currentChild() {
    return this.globalData.children.find((item) => item.studentId === this.globalData.childId) || null
  }
})
