<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  createGradePartTemplate,
  createGradeRule,
  deleteGradePartTemplate,
  deleteGradeRule,
  fetchGradePartTemplates,
  fetchGradeRules,
  updateGradePartTemplate,
  updateGradeRule,
  type GradePart,
  type GradePartTemplate,
  type GradeRule,
} from '../api/client'

const rules = ref<GradeRule[]>([])
const templates = ref<GradePartTemplate[]>([])
const loading = ref(false)

const ruleOpen = ref(false)
const ruleSaving = ref(false)
const ruleEditingId = ref<number | null>(null)
const ruleForm = reactive({ name: '', minScore: 0, maxScore: 100, sort: 0 })

const templateOpen = ref(false)
const templateSaving = ref(false)
const templateEditingId = ref<number | null>(null)
const templateForm = reactive<{ name: string; courseCategory: string; parts: GradePart[] }>({
  name: '',
  courseCategory: '',
  parts: [],
})

const ruleColumns = [
  { title: '等级名称', dataIndex: 'name', width: 160 },
  { title: '分数区间', dataIndex: 'range', width: 200 },
  { title: '排序', dataIndex: 'sort', width: 90, align: 'center' as const },
  { title: '操作', dataIndex: 'action', width: 120 },
]

const templateColumns = [
  { title: '模板名称', dataIndex: 'name', width: 200 },
  { title: '适用课程类别', dataIndex: 'courseCategory', width: 160 },
  { title: '分项配置', dataIndex: 'parts' },
  { title: '操作', dataIndex: 'action', width: 130 },
]

async function load() {
  loading.value = true
  try {
    const [ruleList, templateList] = await Promise.all([fetchGradeRules(), fetchGradePartTemplates()])
    rules.value = ruleList
    templates.value = templateList
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载成绩设置失败')
  } finally {
    loading.value = false
  }
}

function openRuleModal(record?: GradeRule) {
  ruleEditingId.value = record?.id ?? null
  ruleForm.name = record?.name ?? ''
  ruleForm.minScore = Number(record?.minScore ?? 0)
  ruleForm.maxScore = Number(record?.maxScore ?? 100)
  ruleForm.sort = Number(record?.sort ?? rules.value.length)
  ruleOpen.value = true
}

async function submitRule() {
  if (!ruleForm.name.trim()) {
    message.warning('请填写等级名称')
    return
  }
  if (ruleForm.minScore > ruleForm.maxScore) {
    message.warning('最低分不能大于最高分')
    return
  }
  ruleSaving.value = true
  try {
    const payload = {
      name: ruleForm.name.trim(),
      minScore: Number(ruleForm.minScore),
      maxScore: Number(ruleForm.maxScore),
      sort: Number(ruleForm.sort || 0),
    }
    if (ruleEditingId.value) {
      await updateGradeRule(ruleEditingId.value, payload)
      message.success('等级规则已更新')
    } else {
      await createGradeRule(payload)
      message.success('等级规则已新增')
    }
    ruleOpen.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存等级规则失败')
  } finally {
    ruleSaving.value = false
  }
}

function removeRule(record: GradeRule) {
  Modal.confirm({
    title: '删除等级规则',
    content: `确认删除「${record.name}」？删除后成绩分析将使用默认及格/优秀线。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteGradeRule(record.id)
        message.success('等级规则已删除')
        await load()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    },
  })
}

function openTemplateModal(record?: GradePartTemplate) {
  templateEditingId.value = record?.id ?? null
  templateForm.name = record?.name ?? ''
  templateForm.courseCategory = record?.courseCategory ?? ''
  templateForm.parts = (record?.parts ?? []).map((part) => ({
    name: part.name,
    weight: Number(part.weight ?? 0),
    fullScore: Number(part.fullScore ?? 0),
  }))
  templateOpen.value = true
}

function addTemplatePart() {
  templateForm.parts.push({ name: '', weight: 0, fullScore: 0 })
}

async function submitTemplate() {
  if (!templateForm.name.trim()) {
    message.warning('请填写模板名称')
    return
  }
  const parts = templateForm.parts.filter((part) => part.name.trim())
  if (!parts.length) {
    message.warning('请至少配置一个分项')
    return
  }
  const weightSum = parts.reduce((sum, part) => sum + Number(part.weight || 0), 0)
  if (Math.abs(weightSum - 100) > 0.01) {
    message.warning(`分项权重合计应为 100%，当前为 ${weightSum}%`)
    return
  }
  templateSaving.value = true
  try {
    const payload = {
      name: templateForm.name.trim(),
      courseCategory: templateForm.courseCategory.trim() || undefined,
      parts: parts.map((part, index) => ({
        name: part.name.trim(),
        weight: Number(part.weight || 0),
        fullScore: Number(part.fullScore || 0),
        sort: index,
      })),
    }
    if (templateEditingId.value) {
      await updateGradePartTemplate(templateEditingId.value, payload)
      message.success('分项模板已更新')
    } else {
      await createGradePartTemplate(payload)
      message.success('分项模板已新增')
    }
    templateOpen.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存分项模板失败')
  } finally {
    templateSaving.value = false
  }
}

function removeTemplate(record: GradePartTemplate) {
  Modal.confirm({
    title: '删除分项模板',
    content: `确认删除「${record.name}」？`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteGradePartTemplate(record.id)
        message.success('分项模板已删除')
        await load()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    },
  })
}

function partsText(record: GradePartTemplate) {
  return record.parts.map((part) =>
    part.weight ? `${part.name} ${part.weight}%` : `${part.name} ${part.fullScore ?? 0}分`,
  ).join('、') || '—'
}

onMounted(load)
</script>

<template>
  <div class="grade-page">
    <a-spin :spinning="loading">
      <div class="panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">成绩等级规则</div>
            <div class="panel-desc">用于等级制考试的可选等级，以及成绩分析中的及格线、优秀线（名称含「合格/及格」「优秀」的规则生效）</div>
          </div>
          <a-button type="primary" size="small" @click="openRuleModal()">新增等级</a-button>
        </div>
        <a-table
          class="grade-table"
          :columns="ruleColumns"
          :data-source="rules"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'range'">{{ record.minScore }} ~ {{ record.maxScore }} 分</span>
            <span v-else-if="column.dataIndex === 'sort'">{{ record.sort }}</span>
            <span v-else-if="column.dataIndex === 'action'" class="row-actions">
              <a-button type="link" size="small" @click="openRuleModal(record)">编辑</a-button>
              <a-button type="link" size="small" danger @click="removeRule(record)">删除</a-button>
            </span>
          </template>
          <template #emptyText>
            <a-empty description="暂无等级规则，未配置时及格线按满分 60%、优秀线按满分 85% 计算" />
          </template>
        </a-table>
      </div>

      <div class="panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">成绩分项模板</div>
            <div class="panel-desc">在新建考试时可一键导入分项配置，例如书法课的「笔画、结构、章法」</div>
          </div>
          <a-button type="primary" size="small" @click="openTemplateModal()">新增模板</a-button>
        </div>
        <a-table
          class="grade-table"
          :columns="templateColumns"
          :data-source="templates"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'courseCategory'">{{ record.courseCategory ?? '通用' }}</span>
            <span v-else-if="column.dataIndex === 'parts'" class="ellipsis">{{ partsText(record) }}</span>
            <span v-else-if="column.dataIndex === 'action'" class="row-actions">
              <a-button type="link" size="small" @click="openTemplateModal(record)">编辑</a-button>
              <a-button type="link" size="small" danger @click="removeTemplate(record)">删除</a-button>
            </span>
          </template>
          <template #emptyText>
            <a-empty description="暂无分项模板，可按课程维护常用分项" />
          </template>
        </a-table>
      </div>
    </a-spin>

    <a-modal
      v-model:open="ruleOpen"
      :title="ruleEditingId ? '编辑等级规则' : '新增等级规则'"
      :confirm-loading="ruleSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitRule"
    >
      <a-form layout="vertical">
        <a-form-item label="等级名称" required>
          <a-input v-model:value="ruleForm.name" placeholder="例如：优秀、良好、合格、待提高" :maxlength="20" />
        </a-form-item>
        <div class="form-grid">
          <a-form-item label="最低分">
            <a-input-number v-model:value="ruleForm.minScore" :min="0" style="width: 100%" />
          </a-form-item>
          <a-form-item label="最高分">
            <a-input-number v-model:value="ruleForm.maxScore" :min="0" style="width: 100%" />
          </a-form-item>
        </div>
        <a-form-item label="排序">
          <a-input-number v-model:value="ruleForm.sort" :min="0" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="templateOpen"
      :title="templateEditingId ? '编辑分项模板' : '新增分项模板'"
      width="640"
      :confirm-loading="templateSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitTemplate"
    >
      <a-form layout="vertical">
        <div class="form-grid">
          <a-form-item label="模板名称" required>
            <a-input v-model:value="templateForm.name" placeholder="例如：书法课分项" :maxlength="64" />
          </a-form-item>
          <a-form-item label="适用课程类别">
            <a-input v-model:value="templateForm.courseCategory" placeholder="选填，如 书法" :maxlength="64" />
          </a-form-item>
        </div>
        <a-form-item label="分项配置（权重合计 100%）">
          <div class="part-toolbar">
            <a-button size="small" @click="addTemplatePart">+ 添加分项</a-button>
          </div>
          <div v-if="!templateForm.parts.length" class="part-empty">尚未配置分项</div>
          <div v-for="(part, index) in templateForm.parts" :key="index" class="part-row">
            <a-input v-model:value="part.name" placeholder="分项名称" style="flex: 1" />
            <a-input-number v-model:value="part.weight" :min="0" :max="100" addon-after="%" style="width: 130px" />
            <a-input-number v-model:value="part.fullScore" :min="0" placeholder="分项满分" style="width: 130px" />
            <a-button type="link" size="small" danger @click="templateForm.parts.splice(index, 1)">删除</a-button>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.grade-page {
  min-height: 100%;
  background: #fff;
  padding: 16px 24px 24px;
}

.panel {
  padding: 16px;
  border: 1px solid #f0f1f2;
  border-radius: 6px;
  margin-bottom: 16px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.panel-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.panel-desc {
  margin-top: 4px;
  color: var(--text-2);
  font-size: 12px;
}

.grade-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 9px 8px;
}

.grade-table :deep(.ant-table-tbody > tr > td) {
  padding: 8px;
  font-size: 13px;
}

.ellipsis {
  display: inline-block;
  max-width: 480px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.row-actions {
  display: inline-flex;
  gap: 2px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.part-toolbar {
  margin-bottom: 8px;
}

.part-empty {
  padding: 6px 10px;
  background: #fafbfc;
  border: 1px dashed #e5e6eb;
  border-radius: 4px;
  color: var(--text-2);
  font-size: 12px;
}

.part-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
