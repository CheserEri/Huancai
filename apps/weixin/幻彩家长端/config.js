// 家长端接口地址：当前指向腾讯云线上服务器（Nginx 80 端口反代到后端）。
// 开发/真机调试需在开发者工具「详情 → 本地设置」勾选「不校验合法域名」；体验版/正式版须使用已备案的 HTTPS 域名。
module.exports = {
  baseUrl: 'http://43.143.100.118/api/wxapp',
  brandName: '幻彩学堂',
  // 订阅消息模板（活动开始前提醒），需与后端 wx.miniapp.subscribe.activity-template-id 一致
  activityTemplateId: 'wx-KOL04qZ6EXqwf56Haxe09Bd3PRY-x3m3wBHiv5r4'
}
