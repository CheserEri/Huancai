<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ExportOutlined,
  LockOutlined,
  PlusOutlined,
  SearchOutlined,
  SwapOutlined,
} from '@ant-design/icons-vue'
import { getPageConfig } from '../config/pages'
import { menuIndex } from '../config/menu'

const route = useRoute()
const entry = computed(() => menuIndex.get(route.path))
const config = computed(() => getPageConfig(route.path))
const status = computed(() => entry.value?.status ?? 'ok')
const activeTab = ref(0)
const keyword = ref('')

const tableColumns = computed(() =>
  config.value.columns.map((column) => ({
    title: column.title,
    dataIndex: column.dataIndex,
    width: column.width,
    align: column.align,
    money: column.money,
  })),
)

function formatMoney(value: unknown) {
  const num = Number(value ?? 0)
  return `¥${num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
</script>

<template>
  <div class="page">
    <div v-if="status === 'locked'" class="state-card">
      <div class="state-icon"><LockOutlined /></div>
      <div class="state-title">暂无访问权限</div>
      <div class="state-desc">
        当前账号没有「{{ entry?.title }}」的权限。原系统里这是由员工角色与数据范围控制的功能权限，
        复刻版会沿用「角色 → 功能」双层模型，由管理员在内部管理 → 员工中分配。
      </div>
      <a-button type="primary">联系管理员开通</a-button>
    </div>

    <div v-else-if="status === 'external'" class="state-card">
      <div class="state-icon" style="background: #f0f7ff; color: #007aff"><SwapOutlined /></div>
      <div class="state-title">{{ entry?.title }}为外部服务</div>
      <div class="state-desc">
        该能力不在核心系统内，点击会跳转到独立的第三方站点（原地址：{{ entry?.source }}）。
        自建系统里这类能力建议直接采购，不做复刻。
      </div>
      <a-button>查看外部地址</a-button>
    </div>

    <template v-else-if="config.columns.length === 0">
      <div class="state-card">
        <div class="state-icon"><SwapOutlined /></div>
        <div class="state-title">{{ entry?.title }}</div>
        <div class="state-desc">该模块在复刻版中第一版先保留入口与布局，业务逻辑随后端接口落地后接入。</div>
      </div>
    </template>

    <template v-else>
      <div class="filter-card">
        <div v-if="config.tabs && config.tabs.length" class="tabs-line">
          <div
            v-for="(tab, index) in config.tabs"
            :key="tab"
            class="tab"
            :class="{ active: activeTab === index }"
            @click="activeTab = index"
          >
            {{ tab }}
          </div>
        </div>
        <div class="filter-row">
          <div v-for="filter in config.filters" :key="filter" class="filter-item">
            <span>{{ filter }}</span>
            <a-input :placeholder="`请输入${filter}`" style="width: 150px" size="small" />
          </div>
          <a-button type="primary" size="small"><SearchOutlined />查询</a-button>
          <a-button size="small">重置</a-button>
        </div>
      </div>

      <div class="table-card">
        <div class="table-toolbar">
          <div class="table-toolbar-left">
            <a-button type="primary" v-if="config.primary" size="small">
              <PlusOutlined />{{ config.primary }}
            </a-button>
            <a-button size="small">批量操作</a-button>
            <a-input
              v-model:value="keyword"
              placeholder="搜索关键字"
              size="small"
              style="width: 180px"
              allow-clear
            />
          </div>
          <a-button size="small"><ExportOutlined />导出当前结果</a-button>
        </div>

        <div v-if="config.summary" class="summary-bar">
          <span v-for="item in config.summary" :key="item.label">{{ item.label }}<b>{{ item.value }}</b></span>
        </div>

        <a-table
          :columns="tableColumns"
          :data-source="config.rows"
          :pagination="{ pageSize: 10, showSizeChanger: false, showTotal: (total: number) => `共 ${total} 条` }"
          row-key="id"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="(column as any).money">{{ formatMoney((record as any)[(column as any).dataIndex]) }}</span>
            <span v-else>{{ (record as any)[(column as any).dataIndex] }}</span>
          </template>
          <template #emptyText>
            <div style="padding: 32px 0; color: #8f959e">暂无数据</div>
          </template>
        </a-table>
      </div>
    </template>
  </div>
</template>
