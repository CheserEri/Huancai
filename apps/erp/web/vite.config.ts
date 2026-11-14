import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    host: true,
    // 允许通过局域网 IP、主机名或反向代理域名访问开发服务器
    allowedHosts: true,
    // 允许局域网设备带 Origin 发起 POST（否则 Vite 默认只信任 localhost 来源，返回 403）
    cors: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      // 班级相册等上传文件由后端 /uploads 提供
      '/uploads': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
