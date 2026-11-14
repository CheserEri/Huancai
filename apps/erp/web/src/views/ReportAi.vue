<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  MinusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { fetchInsights, type InsightRow } from '../api/client'

const campus = ref<'all' | number>('all')
const insights = ref<InsightRow[]>([])
const keyword = ref('')
const loading = ref(false)

const questions = [
  { label: '本月收款', keyword: '本月收款' },
  { label: '到课率', keyword: '到课率' },
  { label: '老师课时', keyword: '教师' },
  { label: '欠费', keyword: '欠费' },
  { label: '招生转化', keyword: '招生转化' },
  { label: '课时储备', keyword: '课时储备' },
]

async function load() {
  loading.value = true
  try {
    insights.value = await fetchInsights({
      campusId: campus.value === 'all' ? undefined : Number(campus.value),
    })
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function onCampusChange() {
  load()
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return insights.value
  return insights.value.filter((row) =>
    [row.title, row.detail, row.value, row.tag].some((field) =>
      String(field ?? '').toLowerCase().includes(kw),
    ),
  )
})

const tagStyles: Record<string, { background: string; color: string }> = {
  招生: { background: '#e8f2ff', color: '#1677ff' },
  教务: { background: '#e6fffb', color: '#13c2c2' },
  财务: { background: '#fff7e6', color: '#fa8c16' },
  师资: { background: '#f6f1fe', color: '#722ed1' },
}

function tagStyle(tag: string) {
  return tagStyles[tag] ?? { background: '#f2f3f5', color: '#646a73' }
}

interface TrendMeta {
  icon: Component
  color: string
  text: string
}

function trendMeta(trend: InsightRow['trend']): TrendMeta {
  if (trend === 'up') return { icon: ArrowUpOutlined, color: '#52c41a', text: '上升' }
  if (trend === 'down') return { icon: ArrowDownOutlined, color: '#f5222d', text: '下降' }
  return { icon: MinusOutlined, color: '#8f959e', text: '持平' }
}

function pickQuestion(value: string) {
  keyword.value = value
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <span>校区</span>
          <a-select v-model:value="campus" size="small" style="width: 150px" @change="onCampusChange">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
            <a-select-option :value="2">新民幻彩国学堂</a-select-option>
          </a-select>
        </div>
        <a-button type="primary" size="small" :loading="loading" @click="load">
          <ReloadOutlined />刷新
        </a-button>
        <div class="filter-item search-item">
          <a-input
            v-model:value="keyword"
            size="small"
            allow-clear
            placeholder="问点什么，例如：本月收款 / 到课率 / 老师课时 / 欠费"
          >
            <template #prefix>
              <SearchOutlined style="color: #bfc4cc" />
            </template>
          </a-input>
        </div>
      </div>

      <div class="chips">
        <span class="chips-label">常用问题</span>
        <button
          v-for="item in questions"
          :key="item.label"
          type="button"
          class="chip"
          :class="{ active: keyword === item.keyword }"
          @click="pickQuestion(item.keyword)"
        >
          {{ item.label }}
        </button>
      </div>

      <div class="source-note muted">
        本地规则引擎基于本系统真实数据生成（订单、收款、点名、咨询），不调用外部大模型。
      </div>
    </div>

    <div class="result-head">
      <span class="card-title">经营洞察</span>
      <span class="muted" style="font-size: 12px">
        共 {{ filtered.length }} 条<span v-if="keyword.trim()">（关键词：{{ keyword.trim() }}）</span>
      </span>
    </div>

    <a-spin :spinning="loading">
      <div v-if="!filtered.length" class="empty-card">
        {{ insights.length ? '没有匹配的洞察，换个关键词试试' : '暂无洞察数据' }}
      </div>
      <div v-else class="insight-grid">
        <div v-for="row in filtered" :key="row.key" class="insight-card">
          <div class="insight-head">
            <span class="tag" :style="tagStyle(row.tag)">{{ row.tag || '洞察' }}</span>
            <span class="trend" :style="{ color: trendMeta(row.trend).color }">
              <component :is="trendMeta(row.trend).icon" />
              {{ trendMeta(row.trend).text }}
            </span>
          </div>
          <div class="insight-body">
            <div class="insight-main">
              <div class="insight-title">{{ row.title || '未命名洞察' }}</div>
              <div class="insight-detail">{{ row.detail || '暂无详细说明' }}</div>
            </div>
            <div class="insight-value">{{ row.value || '—' }}</div>
          </div>
        </div>
      </div>
    </a-spin>

    <div class="foot-note muted">
      共 {{ insights.length }} 条洞察 · 数据随本月经营实时变化，点击刷新获取最新结果。
    </div>
  </div>
</template>

<style scoped>
.search-item {
  flex: 0 1 380px;
  min-width: 240px;
}

.search-item :deep(.ant-input-affix-wrapper) {
  width: 100%;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.chips-label {
  font-size: 12px;
  color: var(--text-3);
  margin-right: 2px;
}

.chip {
  padding: 4px 12px;
  border-radius: 14px;
  border: 1px solid #e5e8ec;
  background: #fafbfc;
  color: var(--text-2);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.15s, background 0.15s, border-color 0.15s;
}

.chip:hover {
  color: var(--brand);
  border-color: #bcd9ff;
  background: #f3f8ff;
}

.chip.active {
  color: #fff;
  background: var(--brand);
  border-color: var(--brand);
}

.source-note {
  margin-bottom: 12px;
  font-size: 12px;
}

.result-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin: 16px 2px 10px;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.insight-card {
  background: var(--card);
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.15s;
}

.insight-card:hover {
  box-shadow: 0 4px 14px rgba(31, 35, 41, 0.08);
}

.insight-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 18px;
}

.trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.insight-body {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.insight-main {
  flex: 1;
  min-width: 0;
}

.insight-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.insight-detail {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-2);
}

.insight-value {
  flex: 0 0 auto;
  max-width: 45%;
  text-align: right;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.25;
  color: var(--text);
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}

.empty-card {
  background: var(--card);
  border-radius: 8px;
  padding: 48px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--text-3);
}

.foot-note {
  margin-top: 14px;
  font-size: 12px;
  text-align: right;
}

@media (max-width: 1100px) {
  .insight-grid {
    grid-template-columns: 1fr;
  }
  .insight-value {
    max-width: 40%;
  }
}
</style>
