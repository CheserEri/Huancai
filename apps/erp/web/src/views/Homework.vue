<script setup lang="ts">
import { ref } from 'vue'

interface HomeworkRow {
  id: number
  assignedAt: string
  className: string
  courseName: string
  teacherName: string
  title: string
  submittedCount: number
  status: string
}

const rows = ref<HomeworkRow[]>([])
</script>

<template>
  <div class="page">
    <a-alert
      class="notice"
      type="info"
      show-icon
      message="暂无作业数据，布置作业后将在此展示。"
    />

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">作业总数</div>
        <div class="stat-value">0</div>
        <div class="stat-sub">暂无数据</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已提交</div>
        <div class="stat-value">0</div>
        <div class="stat-sub">暂无数据</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待批改</div>
        <div class="stat-value">0</div>
        <div class="stat-sub">暂无数据</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">平均分</div>
        <div class="stat-value">—</div>
        <div class="stat-sub">暂无数据</div>
      </div>
    </div>

    <div class="table-card">
      <a-table :data-source="rows" :pagination="false" row-key="id" size="middle" :scroll="{ x: 1000 }">
        <a-table-column title="布置时间" data-index="assignedAt" width="160" />
        <a-table-column title="班级" data-index="className" width="140" ellipsis />
        <a-table-column title="课程" data-index="courseName" width="140" ellipsis />
        <a-table-column title="教师" data-index="teacherName" width="100" />
        <a-table-column title="作业标题" data-index="title" ellipsis />
        <a-table-column title="提交人数" data-index="submittedCount" width="110" />
        <a-table-column title="状态" data-index="status" width="110" />
        <template #emptyText>
          <div style="padding: 32px 0; color: #8f959e">暂无作业数据</div>
        </template>
      </a-table>

      <div class="muted" style="margin-top: 12px; font-size: 12px">数据来源：原系统作业模块（待开通权限）</div>
    </div>
  </div>
</template>

<style scoped>
.notice {
  margin-bottom: 12px;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.stat-label {
  font-size: 12px;
  color: #8f959e;
}
.stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
  color: #1d2129;
}
.stat-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #8f959e;
}
</style>
