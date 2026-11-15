/**
 * 家长端请求封装：自动静默登录、携带 Bearer token、401 时重新登录重试一次。
 */
const config = require('../config')

const TOKEN_KEY = 'wxapp_token'
const DEV_KEY = 'wxapp_dev_openid'
let token = wx.getStorageSync(TOKEN_KEY) || ''
let loginPromise = null

function deviceId() {
  let id = wx.getStorageSync(DEV_KEY)
  if (!id) {
    id = 'dev-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
    wx.setStorageSync(DEV_KEY, id)
  }
  return id
}

function wxLogin() {
  return new Promise((resolve) => {
    wx.login({
      success: (res) => resolve(res.code || ''),
      fail: () => resolve('')
    })
  })
}

function raw(path, options) {
  const opts = options || {}
  const header = { 'content-type': 'application/json' }
  if (token) {
    header.Authorization = 'Bearer ' + token
  }
  return new Promise((resolve, reject) => {
    wx.request({
      url: config.baseUrl + path,
      method: opts.method || 'GET',
      data: opts.data || {},
      header,
      timeout: 15000,
      success: (res) => {
        const body = res.data || {}
        if (res.statusCode === 401) {
          token = ''
          wx.removeStorageSync(TOKEN_KEY)
          reject({ code: 401, message: body.message || '登录已失效' })
          return
        }
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        reject({ code: body.code || -1, message: body.message || '请求失败' })
      },
      fail: () => reject({ code: -1, message: '网络连接失败，请确认后端已启动' })
    })
  })
}

function login() {
  return wxLogin().then((code) => raw('/login', {
    method: 'POST',
    data: { code, devOpenid: deviceId() }
  })).then((data) => {
    token = data.token
    wx.setStorageSync(TOKEN_KEY, token)
    wx.setStorageSync('wxapp_children', data.children || [])
    const app = getApp()
    if (app) {
      app.globalData.profile = data.parent
      app.setChildren(data.children || [])
    }
    return data
  })
}

function ensureToken() {
  if (token) {
    return Promise.resolve(token)
  }
  if (!loginPromise) {
    loginPromise = login().finally(() => {
      loginPromise = null
    })
  }
  return loginPromise
}

function autoLogin() {
  return ensureToken()
    .then(() => raw('/profile'))
    .then((data) => {
      const result = {
        token,
        parent: data.parent,
        children: data.children || []
      }
      const app = getApp()
      if (app) {
        app.globalData.profile = result.parent
        app.setChildren(result.children)
      }
      return result
    })
    .catch((err) => {
      if (err && err.code === 401) {
        return login().then(() => autoLogin())
      }
      throw err
    })
}

function request(path, options) {
  return ensureToken()
    .then(() => raw(path, options))
    .catch((err) => {
      if (err && err.code === 401) {
        return ensureToken().then(() => raw(path, options))
      }
      throw err
    })
}

module.exports = {
  autoLogin,
  login,
  request,
  getToken: () => token,
  clearToken() {
    token = ''
    wx.removeStorageSync(TOKEN_KEY)
  },
  deviceId
}
