import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import 'ant-design-vue/dist/reset.css'
import './style.css'
import App from './App.vue'
import router from './router'

// 全局使用中文，日期选择器中的月份、星期才会显示中文
dayjs.locale('zh-cn')

createApp(App).use(Antd).use(router).mount('#app')
