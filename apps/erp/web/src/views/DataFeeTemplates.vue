<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import {
  createDataFeeTemplate,
  deleteDataFeeTemplate,
  fetchDataFeeTemplates,
  updateDataFeeTemplate,
  type DataFeeTemplateRow,
} from '../api/client'

const rows = ref<DataFeeTemplateRow[]>([])
const loading = ref(false)
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<{
  name: string
  price: number | null
  cycleMonths: number
  status: string
  remark: string
}>({
  name: '',
  price: null,
  cycleMonths: 1,
  status: '启用',
  remark: '',
})

const cycleOptions = [1, 2, 3, 6, 12].map((value) => ({ value, label: cycleText(value) }))

const columns = [
  { title: '模板名称', dataIndex: 'name', width: 240 },
  { title: '资料费价格', dataIndex: 'price', width: 130, align: 'right' as const },
  { title: '缴纳周期', dataIndex: 'cycleMonths', width: 120 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '备注', dataIndex: 'remark' },
  { title: '操作', dataIndex: 'action', width: 140 },
]

function cycleText(months: number | null | undefined) {
  const value = Number(months ?? 1)
  if (value === 1) return '每月'
  if (value === 2) return '每2个月'
  if (value === 3) return '每季度'
  if (value === 6) return '每半年'
  if (value === 12) return '每年'
  return `每${value}个月`
}

function money(value: number | null | undefined) {
  return `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}

function show(value: unknown) {
  return value === null || value === undefined || value === '' ? '—' : value
}

async function load() {
  loading.value = true
  try {
    rows.value = await fetchDataFeeTemplates()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载资料费模板失败')
  } finally {
    loading.value = false
  }
}

function openModal(record?: DataFeeTemplateRow) {
  editingId.value = record ? record.id : null
  form.name = record?.name ?? ''
  form.price = record ? Number(record.price) : null
  form.cycleMonths = record?.cycleMonths ?? 1
  form.status = record?.status ?? '启用'
  form.remark = record?.remark ?? ''
  modalOpen.value = true
}

async function submit() {
  const name = form.name.trim()
  if (!name) {
    message.warning('请填写模板名称')
    return
  }
  if (!form.price || Number(form.price) <= 0) {
    message.warning('资料费价格必须大于 0')
    return
  }
  saving.value = true
  try {
    const payload = {
      name,
      price: Number(form.price),
      cycleMonths: form.cycleMonths,
      status: form.status,
      remark: form.remark.trim() || undefined,
    }
    if (editingId.value) {
      await updateDataFeeTemplate(editingId.value, payload)
      message.success('资料费模板已更新')
    } else {
      await createDataFeeTemplate(payload)
      message.success('资料费模板已创建')
    }
    modalOpen.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存资料费模板失败')
  } finally {
    saving.value = false
  }
}

function remove(record: DataFeeTemplateRow) {
  Modal.confirm({
    title: '删除资料费模板',
    content: `确认删除「${record.name}」？已有缴费记录的模板不能删除，可改为停用。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteDataFeeTemplate(record.id)
        message.success('资料费模板已删除')
        await load()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除资料费模板失败')
      }
    },
  })
}

onMounted(load)
</script>

<template>
  <div class="material-page">
    <div class="filter-line">
      <a-button size="small" type="primary" class="with-icon" @click="openModal()">
        <PlusOutlined />新建资料费模板
      </a-button>
      <span class="filter-label">资料费为 A4 打印资料，不占用教材库存；模板包含价格与缴纳周期</span>
    </div>

    <div class="summary-line">当前结果：共 {{ rows.length }} 条记录</div>

    <a-table
      class="buy-table"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <span v-if="column.dataIndex === 'name'" class="ellipsis">{{ record.name }}</span>
        <span v-else-if="column.dataIndex === 'price'">{{ money(Number(record.price)) }}</span>
        <span v-else-if="column.dataIndex === 'cycleMonths'">{{ cycleText(record.cycleMonths) }}</span>
        <span v-else-if="column.dataIndex === 'status'">
          <span :class="record.status === '启用' ? 'status-on' : 'status-off'">{{ record.status }}</span>
        </span>
        <span v-else-if="column.dataIndex === 'remark'">{{ show(record.remark) }}</span>
        <span v-else-if="column.dataIndex === 'action'" class="row-actions">
          <a-button type="link" size="small" class="row-btn" @click="openModal(record)">编辑</a-button>
          <a-button type="link" size="small" danger class="row-btn" @click="remove(record)">删除</a-button>
        </span>
      </template>
      <template #emptyText>
        <a-empty description="暂无资料费模板，点击左上角新建" />
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑资料费模板' : '新建资料费模板'"
      width="520"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submit"
    >
      <a-form layout="vertical" class="material-form">
        <a-form-item label="模板名称" required>
          <a-input v-model:value="form.name" :maxlength="60" placeholder="例如：月度资料费、季度资料费" />
        </a-form-item>
        <div class="form-grid">
          <a-form-item label="资料费价格" required>
            <a-input-number v-model:value="form.price" :min="0.01" :precision="2" style="width: 100%" />
          </a-form-item>
          <a-form-item label="缴纳周期" required>
            <a-select v-model:value="form.cycleMonths" :options="cycleOptions" />
          </a-form-item>
        </div>
        <a-form-item label="状态">
          <a-radio-group v-model:value="form.status">
            <a-radio value="启用">启用</a-radio>
            <a-radio value="停用">停用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark" :maxlength="255" placeholder="选填" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.material-page {
  min-height: 100%;
  background: #fff;
  padding: 0 24px 20px;
}

.filter-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px 0 12px;
  font-size: 13px;
}

.filter-label {
  color: var(--text-2);
}

.summary-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.buy-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.buy-table :deep(.ant-table-tbody > tr > td) {
  padding: 9px 8px;
  font-size: 13px;
  border-right: 1px solid #f5f6f7;
}

.status-on {
  color: var(--brand);
}

.status-off {
  color: #8f959e;
}

.row-actions {
  display: inline-flex;
  gap: 4px;
}

.ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-form :deep(.ant-form-item) {
  margin-bottom: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}
</style>
