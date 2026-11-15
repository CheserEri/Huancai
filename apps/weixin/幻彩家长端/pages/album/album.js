const api = require('../../utils/api')
const util = require('../../utils/util')
const config = require('../../config')

const app = getApp()
// 后端照片地址是 /uploads/... 相对路径，小程序需要拼上服务地址才能显示
const FILE_BASE = config.baseUrl.replace(/\/api\/wxapp\/?$/, '')

Page({
  data: {
    loading: true,
    error: '',
    noChild: false,
    child: null,
    albums: [],
    totalPhotos: 0
  },

  onLoad(options) {
    this.classId = Number((options && options.classId) || 0)
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
        const children = app.globalData.children || []
        if (!children.length) {
          this.setData({ loading: false, noChild: true, albums: [], totalPhotos: 0, child: null })
          return null
        }
        let childId = app.globalData.childId
        if (!children.some((item) => item.studentId === childId)) {
          childId = children[0].studentId
          app.setChild(childId)
        }
        const child = children.find((item) => item.studentId === childId)
        this.setData({ child: child ? { ...child, initial: (child.name || '学').slice(0, 1) } : null })
        return api.request('/albums?studentId=' + childId)
      })
      .then((list) => {
        if (!list) return
        let albums = (list || []).map((album) => ({
          ...album,
          latestText: util.dateTime(album.latestAt),
          photos: (album.photos || []).map((photo) => ({
            ...photo,
            src: FILE_BASE + photo.url,
            dateText: util.dateTime(photo.createdAt)
          }))
        }))
        if (this.classId) {
          albums = albums.filter((album) => Number(album.classId) === this.classId)
        }
        this.setData({
          loading: false,
          noChild: false,
          albums,
          totalPhotos: albums.reduce((sum, album) => sum + album.photos.length, 0)
        })
      })
      .catch((err) => {
        this.setData({ loading: false, error: err.message || '加载班级相册失败' })
      })
      .finally(() => {
        wx.stopPullDownRefresh()
      })
  },

  preview(event) {
    const albumIndex = Number(event.currentTarget.dataset.albumIndex)
    const index = Number(event.currentTarget.dataset.index)
    const album = this.data.albums[albumIndex]
    if (!album || !album.photos.length) return
    const urls = album.photos.map((photo) => photo.src)
    wx.previewImage({ current: urls[index], urls })
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

  retry() {
    this.load()
  }
})
