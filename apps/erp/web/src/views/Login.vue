<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  LockOutlined,
  MobileOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { login } from '../api/client'
import { hasMenuPermission, HOME_PATH, saveLogin } from '../stores/auth'

const router = useRouter()
const route = useRoute()

const mode = ref<'phone' | 'account'>('phone')
const account = ref('')
const password = ref('')
const error = ref('')
const remember = ref(true)
const loading = ref(false)

async function submit() {
  error.value = ''
  if (!account.value || !password.value) {
    error.value = mode.value === 'phone' ? '请输入手机号码和密码' : '请输入账号和密码'
    return
  }
  loading.value = true
  try {
    const data = await login(account.value.trim(), password.value)
    saveLogin(data)
    const redirect = (route.query.redirect as string) || ''
    router.replace(redirect && hasMenuPermission(redirect, data.permissions) ? redirect : HOME_PATH)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const products = [
  { name: '课时记账', desc: '学员课时实时对账', color: '#FF8A00', icon: '记' },
  { name: '家长查询', desc: '课时余额随时可查', color: '#FF5B22', icon: '查' },
  { name: '招生表单', desc: '线索自动进系统', color: '#13B5EA', icon: '招' },
]
</script>

<template>
  <div class="login">
    <header class="lp-header">
      <div class="lp-brand">
        <img class="lp-logo" src="/logo.png" alt="幻彩学堂" />
      </div>
      <nav class="lp-nav">
        <a>申请使用</a>
        <a>官网</a>
        <a>周边</a>
        <a>帮助</a>
        <a>公众号</a>
      </nav>
    </header>

    <main class="lp-main">
      <div class="lp-card">
        <section class="lp-promo">
          <div class="promo-blob promo-blob-1" />
          <div class="promo-blob promo-blob-2" />
          <div class="promo-inner">
            <div class="promo-title">课时清零 <b>0</b> 差错</div>
            <div class="promo-sub">两校区一套账<br />结课退费不扯皮</div>
            <div class="promo-cta">点击查看系统功能清单</div>
          </div>
        </section>

        <section class="lp-form">
          <div class="lp-tabs">
            <span :class="{ active: mode === 'phone' }" @click="mode = 'phone'">手机号码登录</span>
            <span :class="{ active: mode === 'account' }" @click="mode = 'account'">账号登录</span>
          </div>

          <form class="lp-fields" @submit.prevent="submit">
            <div class="lp-field">
              <MobileOutlined v-if="mode === 'phone'" class="field-icon" />
              <UserOutlined v-else class="field-icon" />
              <input
                v-model="account"
                :placeholder="mode === 'phone' ? '请输入手机号码' : '请输入账号'"
                autocomplete="username"
              />
            </div>
            <div class="lp-field">
              <LockOutlined class="field-icon" />
              <input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" />
            </div>

            <div class="lp-links">
              <a>手机号码重置密码</a>
              <a>如何登录？</a>
            </div>

            <p v-if="error" class="lp-error">{{ error }}</p>

            <button class="lp-submit" type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>

            <a-checkbox v-model:checked="remember" class="lp-remember">保存到桌面登录</a-checkbox>
          </form>
        </section>
      </div>

      <div class="lp-foot">
        <div class="foot-products">
          <div class="foot-title">相关产品</div>
          <div class="product-list">
            <div v-for="item in products" :key="item.name" class="product">
              <span class="product-icon" :style="{ background: item.color }">{{ item.icon }}</span>
              <div>
                <div class="product-name">{{ item.name }}</div>
                <div class="product-desc">{{ item.desc }}</div>
              </div>
            </div>
          </div>
        </div>
        <div class="foot-hotline">
          <div class="foot-title">咨询热线</div>
          <div class="hotline">186 0247 7659</div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login {
  min-height: 100%;
  background: #f2f6fc;
  display: flex;
  flex-direction: column;
}

.lp-header {
  height: 60px;
  flex: 0 0 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.lp-brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.lp-logo {
  height: 38px;
  width: auto;
  display: block;
}

.lp-nav {
  display: flex;
  gap: 26px;
  font-size: 13px;
  color: #4e5969;
}

.lp-nav a {
  cursor: pointer;
}

.lp-nav a:hover {
  color: var(--brand);
}

.lp-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 20px 24px;
}

.lp-card {
  width: 1000px;
  max-width: 100%;
  height: 510px;
  background: #fff;
  border-radius: 4px;
  display: flex;
  overflow: hidden;
}

/* ---------- 左侧宣传图 ---------- */
.lp-promo {
  flex: 0 0 560px;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #d8c0f7 0%, #c6a2f6 45%, #b98cf4 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.promo-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  opacity: 0.55;
}

.promo-blob-1 {
  width: 320px;
  height: 320px;
  top: -140px;
  left: -80px;
  background: radial-gradient(circle, #8679eb 0%, rgba(134, 121, 235, 0) 70%);
}

.promo-blob-2 {
  width: 380px;
  height: 380px;
  right: -150px;
  top: -120px;
  background: radial-gradient(circle, #6665e0 0%, rgba(102, 101, 224, 0) 70%);
}

.promo-inner {
  position: relative;
  text-align: center;
  color: #fff;
}

.promo-title {
  font-size: 46px;
  font-weight: 800;
  letter-spacing: 2px;
  text-shadow: 0 3px 10px rgba(78, 30, 140, 0.28);
}

.promo-title b {
  font-size: 62px;
  font-weight: 800;
  margin: 0 2px;
  color: #fff;
}

.promo-sub {
  margin-top: 26px;
  font-size: 27px;
  font-weight: 700;
  line-height: 1.5;
  letter-spacing: 2px;
  text-shadow: 0 3px 10px rgba(78, 30, 140, 0.28);
}

.promo-cta {
  display: inline-block;
  margin-top: 40px;
  padding: 9px 22px;
  border-radius: 4px;
  background: rgba(86, 30, 126, 0.62);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.promo-cta:hover {
  background: rgba(86, 30, 126, 0.8);
}

/* ---------- 右侧表单 ---------- */
.lp-form {
  flex: 1;
  padding: 61px 72px 28px;
  display: flex;
  flex-direction: column;
}

.lp-tabs {
  display: flex;
  gap: 40px;
  font-size: 15px;
  color: #a8abb2;
}

.lp-tabs span {
  padding-bottom: 10px;
  cursor: pointer;
  position: relative;
}

.lp-tabs span.active {
  color: #1f2329;
  font-weight: 600;
}

.lp-tabs span.active::after {
  content: '';
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 4px;
  width: 24px;
  height: 2px;
  background: #2f6bff;
  border-radius: 1px;
}

.lp-fields {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.lp-field {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 46px;
  border-bottom: 1px solid #e5e5e5;
}

.lp-field + .lp-field {
  margin-top: 27px;
}

.field-icon {
  color: #c4c8cf;
  font-size: 15px;
}

.lp-field input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #1f2329;
  height: 100%;
}

.lp-field input::placeholder {
  color: #b9bdc6;
}

.lp-links {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 12px;
  color: #9aa0aa;
}

.lp-links a {
  cursor: pointer;
}

.lp-links a:hover {
  color: var(--brand);
}

.lp-error {
  margin: 10px 0 0;
  font-size: 12px;
  color: #f5222d;
}

.lp-submit {
  width: 100%;
  height: 42px;
  margin-top: auto;
  border: none;
  border-radius: 21px;
  background: var(--brand);
  color: #fff;
  font-size: 15px;
  letter-spacing: 2px;
  cursor: pointer;
}

.lp-submit:hover {
  background: #0a6ce0;
}

.lp-remember {
  margin-top: 14px;
  align-self: center;
  font-size: 12px;
  color: #9aa0aa;
  cursor: pointer;
}

/* ---------- 底部 ---------- */
.lp-foot {
  width: 1000px;
  max-width: 100%;
  margin-top: 22px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.foot-title {
  font-size: 13px;
  color: #1f2329;
  margin-bottom: 14px;
}

.product-list {
  display: flex;
  gap: 40px;
}

.product {
  display: flex;
  align-items: center;
  gap: 8px;
}

.product-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.product-name {
  font-size: 13px;
  color: #1f2329;
}

.product-desc {
  font-size: 11px;
  color: #a8abb2;
  margin-top: 2px;
}

.foot-hotline {
  text-align: left;
}

.foot-hotline .foot-title {
  margin-bottom: 26px;
}

.hotline {
  font-size: 22px;
  color: #1f2329;
  letter-spacing: 1px;
}
</style>
