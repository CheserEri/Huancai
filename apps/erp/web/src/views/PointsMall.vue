<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import {
  createPointsGoods,
  deletePointsGoods,
  fetchPointsGoods,
  updatePointsGoods,
  type PointsGoodsRow,
} from '../api/client'

const rows = ref<PointsGoodsRow[]>([])
const loading = ref(false)
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const keyword = ref('')
const statusFilter = ref<string | undefined>(undefined)

const form = reactive<{
  name: string
  pointsValue: number | null
  stock: number
  status: string
  remark: string
}>({
  name: '',
  pointsValue: null,
  stock: 0,
  status: '正常',
  remark: '',
})

const columns = [
  { title: '兑换物名称', dataIndex: 'name', width: 260 },
  { title: '所需积分', dataIndex: 'pointsValue', width: 160, align: 'right' as const },
  { title: '库存', dataIndex: 'stock', width: 100, align: 'right' as const },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '备注', dataIndex: 'remark' },
  { title: '操作', dataIndex: 'action', width: 140 },
]

/** 积分统一按「xxx 积分」展示，避免与金额混淆。 */
function pointsText(value: number | null | undefined) {
  return `${Number(value ?? 0).toLocaleString('zh-CN')} 积分`
}

function show(value: unknown) {
  return value === null || value === undefined || value === '' ? '—' : value
}

async function load() {
  loading.value = true
  try {
    rows.value = await fetchPointsGoods({
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value,
    })
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载积分兑换物失败')
  } finally {
    loading.value = false
  }
}

function openModal(record?: PointsGoodsRow) {
  editingId.value = record?.id ?? null
  form.name = record?.name ?? ''
  form.pointsValue = record ? Number(record.pointsValue) : null
  form.stock = record ? Number(record.stock) : 0
  form.status = record?.status ?? '正常'
  form.remark = record?.remark ?? ''
  modalOpen.value = true
}

async function submit() {
  if (!form.name.trim()) {
    message.warning('请填写兑换物名称')
    return
  }
  if (form.pointsValue === null || Number(form.pointsValue) <= 0) {
    message.warning('所需积分必须大于 0（积分为独立口径，与金额无关）')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      pointsValue: Number(form.pointsValue),
      stock: Number(form.stock ?? 0),
      status: form.status,
      remark: form.remark.trim() || undefined,
    }
    if (editingId.value) {
      await updatePointsGoods(editingId.value, payload)
      message.success('积分兑换物已更新')
    } else {
      await createPointsGoods(payload)
      message.success('积分兑换物已创建')
    }
    modalOpen.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存积分兑换物失败')
  } finally {
    saving.value = false
  }
}

function remove(record: PointsGoodsRow) {
  Modal.confirm({
    title: '删除积分兑换物',
    content: `确认删除「${record.name}」？已产生兑换记录的兑换物不能删除，可改为停用。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deletePointsGoods(record.id)
        message.success('积分兑换物已删除')
        await load()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除积分兑换物失败')
      }
    },
  })
}

onMounted(load)
</script>

<template>
  <div class="points-page">
    <div class="filter-line">
      <a-button size="small" type="primary" class="with-icon" @click="openModal()">
        <PlusOutlined />新建兑换物
      </a-button>
      <a-input-search
        v-model:value="keyword"
        size="small"
        placeholder="搜索兑换物名称"
        allow-clear
        style="width: 200px"
        @search="load"
        @change="!keyword && load()"
      />
      <a-select
        v-model:value="statusFilter"
        size="small"
        style="width: 110px"
        placeholder="全部状态"
        allow-clear
        :options="[
          { label: '正常', value: '正常' },
          { label: '停用', value: '停用' },
        ]"
        @change="load"
      />
      <span class="filter-label">
        积分为独立口径（不折算金额），与账户剩余未用学费、订单金额互不相通
      </span>
    </div>

    <div class="summary-line">当前结果：共 {{ rows.length }} 个兑换物</div>

    <a-table
      class="goods-table"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <span v-if="column.dataIndex === 'name'" class="ellipsis">{{ record.name }}</span>
        <span v-else-if="column.dataIndex === 'pointsValue'" class="points-value">
          {{ pointsText(record.pointsValue) }}
        </span>
        <span v-else-if="column.dataIndex === 'stock'">{{ record.stock }}</span>
        <span v-else-if="column.dataIndex === 'status'">
          <span :class="record.status === '正常' ? 'status-on' : 'status-off'">{{ record.status }}</span>
        </span>
        <span v-else-if="column.dataIndex === 'remark'">{{ show(record.remark) }}</span>
        <span v-else-if="column.dataIndex === 'action'" class="row-actions">
          <a-button type="link" size="small" class="row-btn" @click="openModal(record)">编辑</a-button>
          <a-button type="link" size="small" danger class="row-btn" @click="remove(record)">删除</a-button>
        </span>
      </template>
      <template #emptyText>
        <a-empty description="暂无积分兑换物，点击左上角新建" />
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑积分兑换物' : '新建积分兑换物'"
      width="520"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submit"
    >
      <a-form layout="vertical" class="goods-form">
        <a-form-item label="兑换物名称" required>
          <a-input v-model:value="form.name" :maxlength="60" placeholder="例如：定制书包、绘本套装" />
        </a-form-item>
        <div class="form-grid">
          <a-form-item label="所需积分" required>
            <a-input-number
              v-model:value="form.pointsValue"
              :min="1"
              :precision="0"
              style="width: 100%"
              addon-after="积分"
            />
          </a-form-item>
          <a-form-item label="库存数量" required>
            <a-input-number v-model:value="form.stock" :min="0" :precision="0" style="width: 100%" />
          </a-form-item>
        </div>
        <div class="points-tip">此处只填积分，不涉及金额；兑换时按该积分值扣减学员积分。</div>
        <a-form-item label="状态">
          <a-radio-group v-model:value="form.status">
            <a-radio value="正常">正常</a-radio>
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
.points-page {
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

.goods-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.goods-table :deep(.ant-table-tbody > tr > td) {
  padding: 9px 8px;
  font-size: 13px;
  border-right: 1px solid #f5f6f7;
}

.points-value {
  color: #fa8c16;
  font-weight: 600;
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

.goods-form :deep(.ant-form-item) {
  margin-bottom: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.points-tip {
  margin: -4px 0 14px;
  padding: 6px 10px;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px;
  color: #ad6800;
  font-size: 12px;
}
</style>
