const api = require('../../utils/api')

const app = getApp()

Page({
  data: {
    name: '',
    phone: '',
    submitting: false,
    error: ''
  },

  onNameInput(event) {
    this.setData({ name: event.detail.value, error: '' })
  },

  onPhoneInput(event) {
    this.setData({ phone: event.detail.value, error: '' })
  },

  submit() {
    const name = this.data.name.trim()
    const phone = this.data.phone.trim()
    if (!name) {
      this.setData({ error: '请填写学员姓名' })
      return
    }
    if (!/^1\d{10}$/.test(phone)) {
      this.setData({ error: '请填写 11 位报名手机号' })
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true, error: '' })
    api.request('/bind', {
      method: 'POST',
      data: { name, phone }
    }).then((data) => {
      app.globalData.profile = data.parent
      app.setChildren(data.children || [])
      this.setData({ submitting: false })
      wx.showToast({ title: '绑定成功', icon: 'success' })
      setTimeout(() => {
        if (getCurrentPages().length > 1) {
          wx.navigateBack()
        } else {
          wx.switchTab({ url: '/pages/index/index' })
        }
      }, 700)
    }).catch((err) => {
      this.setData({ submitting: false, error: err.message || '绑定失败，请稍后重试' })
    })
  }
})
