<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { changePassword } from '../api/client'
import { authState, roleText } from '../stores/auth'

const user = computed(() => authState.user)

const pwdOpen = ref(false)
const pwdSaving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

function openPwdModal() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdOpen.value = true
}

async function submitPwd() {
  if (!pwdForm.oldPassword) {
    message.warning('请输入原密码')
    return
  }
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 6) {
    message.warning('新密码至少 6 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  pwdSaving.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    message.success('密码修改成功')
    pwdOpen.value = false
  } catch (e) {
    message.error(e instanceof Error ? e.message : '密码修改失败')
  } finally {
    pwdSaving.value = false
  }
}

const accountRows = computed(() => {
  const current = authState.user
  if (!current) return []
  return [
    {
      key: 'current',
      org: '幻彩教育',
      account: current.account || current.name,
      campus: current.campusName || '全部校区',
      role: roleText(current.role),
      status: '正常',
    },
  ]
})

const columns = [
  { title: '机构名', dataIndex: 'org', key: 'org', width: 200 },
  { title: '登录名', dataIndex: 'account', key: 'account', width: 160 },
  { title: '管理校区', dataIndex: 'campus', key: 'campus', width: 160 },
  { title: '角色', dataIndex: 'role', key: 'role', width: 150 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
]
</script>

<template>
  <div class="page profile">
    <div class="card">
      <div class="section-title">
        <i class="section-bar" />
        <span>基本信息</span>
      </div>
      <div class="info-row">
        <div class="info-item">
          <div class="info-label">员工姓名</div>
          <div class="info-value">{{ user?.name || '—' }}</div>
        </div>
        <div class="info-item">
          <div class="info-label">手机号码</div>
          <div class="info-value">{{ user?.phone || '未绑定' }}</div>
        </div>
        <div class="info-item">
          <div class="info-label">
            <span>密码</span>
            <a-button type="primary" size="small" class="pwd-btn" @click="openPwdModal">
              修改密码
            </a-button>
          </div>
          <div class="info-value password">••••••••••••</div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="section-title">
        <i class="section-bar" />
        <span>账号信息</span>
      </div>
      <a-table
        :columns="columns"
        :data-source="accountRows"
        :pagination="false"
        :scroll="{ x: 770 }"
        row-key="key"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <a-tag v-if="column.key === 'status'" color="success">{{ record.status }}</a-tag>
          <span v-else>{{ record[column.key as keyof typeof record] }}</span>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="pwdOpen"
      title="修改密码"
      width="420px"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="pwdSaving"
      @ok="submitPwd"
    >
      <div class="pwd-form">
        <div class="pwd-field">
          <span class="pwd-label">原密码</span>
          <a-input-password v-model:value="pwdForm.oldPassword" placeholder="请输入原密码" />
        </div>
        <div class="pwd-field">
          <span class="pwd-label">新密码</span>
          <a-input-password v-model:value="pwdForm.newPassword" placeholder="至少 6 位" />
        </div>
        <div class="pwd-field">
          <span class="pwd-label">确认新密码</span>
          <a-input-password v-model:value="pwdForm.confirmPassword" placeholder="请再次输入新密码" />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.profile {
  max-width: 1200px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 22px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.section-bar {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--brand);
}

.info-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px 88px;
  padding-bottom: 4px;
}

.info-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-3);
}

.pwd-btn {
  height: 22px;
  padding: 0 8px;
  font-size: 12px;
}

.info-value {
  margin-top: 8px;
  font-size: 16px;
  color: var(--text);
}

.info-value.password {
  letter-spacing: 1px;
  color: var(--text-2);
}

.pwd-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 0 4px;
}

.pwd-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pwd-label {
  flex: 0 0 76px;
  font-size: 14px;
  color: var(--text-2);
}
</style>
