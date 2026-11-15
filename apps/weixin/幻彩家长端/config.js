// 家长端接口地址：开发者工具联调用本机后端；真机调试需换成电脑局域网 IP，并勾选「不校验合法域名」。
module.exports = {
  baseUrl: 'http://192.168.0.107:8080/api/wxapp',
  brandName: '幻彩学堂',
  // 订阅消息模板（活动开始前提醒），需与后端 wx.miniapp.subscribe.activity-template-id 一致
  activityTemplateId: 'wx-KOL04qZ6EXqwf56Haxe09Bd3PRY-x3m3wBHiv5r4'
}
