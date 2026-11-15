const api = require('../../utils/api')
const util = require('../../utils/util')
const config = require('../../config')

// 后端海报地址是 /uploads/... 相对路径，小程序需要拼上服务地址才能显示
const FILE_BASE = config.baseUrl.replace(/\/api\/wxapp\/?$/, '')

Page({
  data: {
    loading: true,
    error: '',
    list: [],
    ongoing: [],
    upcoming: [],
    ended: [],
    posters: []
  },

  onShow() {
    const focus = wx.getStorageSync('poster_focus_id')
    if (focus) {
      wx.removeStorageSync('poster_focus_id')
      this.focusPosterId = Number(focus) || 0
    }
    this.load()
  },

  onPullDownRefresh() {
    this.load()
  },

  load() {
    this.setData({ loading: true, error: '' })
    // 海报接口失败不影响活动福利展示
    const postersTask = api.request('/posters').catch(() => [])
    return Promise.all([api.request('/activities'), postersTask])
      .then(([list, posters]) => {
        const items = (list || []).map((item) => ({
          ...item,
          tagClass: item.phase === '进行中' ? 'tag-green' : (item.phase === '未开始' ? 'tag-orange' : 'tag-gray'),
          ruleTexts: (item.rules && item.rules.length ? item.rules : [null]).map((rule) => (
            rule ? util.ruleText(rule) : '到店咨询老师了解活动详情'
          )),
          dateText: item.startDate
            ? (item.endDate ? item.startDate + ' 至 ' + item.endDate : item.startDate + ' 起')
            : '长期有效'
        }))
        const posterItems = (posters || []).map((item) => ({
          ...item,
          current: 0,
          images: (item.images || []).map((image) => FILE_BASE + image.url),
          dateText: item.startDate
            ? (item.endDate ? item.startDate + ' 至 ' + item.endDate : item.startDate + ' 起')
            : '长期有效'
        }))
        this.setData({
          loading: false,
          list: items,
          ongoing: items.filter((item) => item.phase === '进行中'),
          upcoming: items.filter((item) => item.phase === '未开始'),
          ended: items.filter((item) => item.phase === '已结束'),
          posters: posterItems
        })
        if (this.focusPosterId) {
          this.scrollToPoster(this.focusPosterId)
          this.focusPosterId = 0
        }
      })
      .catch((err) => {
        this.setData({ loading: false, error: err.message || '加载失败' })
      })
      .finally(() => {
        wx.stopPullDownRefresh()
      })
  },

  // 一次性订阅：家长每授权一次，后端就获得一次活动提醒的发送机会
  onSubscribe() {
    const templateId = config.activityTemplateId
    if (!templateId) {
      wx.showToast({ title: '订阅模板未配置', icon: 'none' })
      return
    }
    wx.requestSubscribeMessage({
      tmplIds: [templateId],
      success: (res) => {
        if (res[templateId] !== 'accept') {
          wx.showToast({ title: '未开启订阅', icon: 'none' })
          return
        }
        api.request('/subscribe', { method: 'POST' })
          .then(() => wx.showToast({ title: '订阅成功', icon: 'success' }))
          .catch(() => wx.showToast({ title: '订阅失败，请稍后重试', icon: 'none' }))
      },
      fail: () => wx.showToast({ title: '订阅失败，请稍后重试', icon: 'none' })
    })
  },

  scrollToPoster(id) {
    const timer = setTimeout(() => {
      clearTimeout(timer)
      wx.createSelectorQuery()
        .select('#poster-' + id)
        .boundingClientRect()
        .selectViewport()
        .scrollOffset()
        .exec((res) => {
          if (!res || !res[0]) return
          const top = res[0].top + (res[1] ? res[1].scrollTop : 0)
          wx.pageScrollTo({ scrollTop: Math.max(top - 20, 0), duration: 300 })
        })
    }, 200)
  },

  onSwiperChange(event) {
    const id = Number(event.currentTarget.dataset.posterId)
    const current = Number(event.detail.current)
    const index = this.data.posters.findIndex((item) => item.id === id)
    if (index < 0) return
    this.setData({ ['posters[' + index + '].current']: current })
  },

  // 部分安卓机型上 <image> 直接加载 http 图片会失败（预览大图却正常），
  // 这里在加载失败时用 wx.downloadFile 取到本地临时文件再展示。
  onPosterImageError(event) {
    const posterIndex = Number(event.currentTarget.dataset.posterIndex)
    const imageIndex = Number(event.currentTarget.dataset.index)
    const poster = this.data.posters[posterIndex]
    if (!poster) return
    const url = poster.images[imageIndex]
    if (!url || url.indexOf('http') !== 0) return
    this.localImages = this.localImages || {}
    const cached = this.localImages[url]
    if (cached) {
      this.setData({ ['posters[' + posterIndex + '].images[' + imageIndex + ']']: cached })
      return
    }
    wx.downloadFile({
      url,
      success: (res) => {
        if (res.statusCode !== 200 || !res.tempFilePath) return
        this.localImages[url] = res.tempFilePath
        this.setData({ ['posters[' + posterIndex + '].images[' + imageIndex + ']']: res.tempFilePath })
      }
    })
  },

  previewPoster(event) {
    const posterIndex = Number(event.currentTarget.dataset.posterIndex)
    const imageIndex = Number(event.currentTarget.dataset.index)
    const poster = this.data.posters[posterIndex]
    if (!poster || !poster.images.length) return
    wx.previewImage({ current: poster.images[imageIndex], urls: poster.images })
    this.markPosterRead(posterIndex)
  },

  markPosterRead(posterIndex) {
    const poster = this.data.posters[posterIndex]
    if (!poster || poster.readFlag) return
    api.request('/posters/' + poster.id + '/read', { method: 'POST' })
      .then(() => {
        this.setData({ ['posters[' + posterIndex + '].readFlag']: 1 })
      })
      .catch(() => {
        // 标记已读失败不打断阅读，下次进入页面会重新同步
      })
  }
})
