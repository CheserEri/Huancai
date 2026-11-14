<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CheckOutlined, DownOutlined, SearchOutlined } from '@ant-design/icons-vue'

interface FilterOption {
  label: string
  value: string | number
}

const props = withDefaults(
  defineProps<{
    label: string
    open: boolean
    type?: 'multi' | 'single' | 'date'
    options?: FilterOption[]
    modelValue?: Array<string | number>
    dateValue?: [string, string] | null
    searchable?: boolean
    emptyText?: string
  }>(),
  {
    type: 'multi',
    options: () => [],
    modelValue: () => [],
    dateValue: null,
    searchable: false,
    emptyText: '暂无可选项',
  },
)

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'apply', value: Array<string | number>): void
  (e: 'apply-date', value: [string, string] | null): void
}>()

const draft = ref<Array<string | number>>([])
const dateStart = ref('')
const dateEnd = ref('')
const keyword = ref('')

const selectedSet = computed(() => new Set(draft.value.map((item) => String(item))))

const filteredOptions = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return props.options
  return props.options.filter((item) => item.label.toLowerCase().includes(text))
})

watch(
  () => props.open,
  (open) => {
    if (!open) return
    draft.value = [...props.modelValue]
    dateStart.value = props.dateValue?.[0] ?? ''
    dateEnd.value = props.dateValue?.[1] ?? ''
    keyword.value = ''
  },
)

function toggle(value: string | number) {
  if (props.type === 'single') {
    draft.value = selectedSet.value.has(String(value)) ? [] : [value]
    return
  }
  draft.value = selectedSet.value.has(String(value))
    ? draft.value.filter((item) => String(item) !== String(value))
    : [...draft.value, value]
}

function clear() {
  draft.value = []
  dateStart.value = ''
  dateEnd.value = ''
}

function apply() {
  if (props.type === 'date') {
    const value =
      dateStart.value && dateEnd.value ? ([dateStart.value, dateEnd.value] as [string, string]) : null
    emit('apply-date', value)
  } else {
    emit('apply', [...draft.value])
  }
  emit('update:open', false)
}

function onOpenChange(open: boolean) {
  emit('update:open', open)
}
</script>

<template>
  <a-popover
    :open="open"
    trigger="click"
    placement="bottomLeft"
    :overlay-style="{ zIndex: 1050 }"
    @open-change="onOpenChange"
  >
    <template #content>
      <div class="sp-filter-panel">
        <div v-if="type !== 'date' && searchable" class="sp-filter-search">
          <SearchOutlined />
          <input v-model="keyword" placeholder="搜索选项" />
        </div>

        <div v-if="type === 'date'" class="sp-filter-date">
          <div class="sp-date-line">
            <input v-model="dateStart" type="date" class="sp-date-input" />
            <span class="sp-date-split">~</span>
            <input v-model="dateEnd" type="date" class="sp-date-input" />
          </div>
          <div class="sp-filter-tip">按订单经办日期筛选，含起止当天</div>
        </div>

        <div v-else class="sp-filter-options">
          <div
            v-for="option in filteredOptions"
            :key="String(option.value)"
            class="sp-filter-option"
            :class="{ selected: selectedSet.has(String(option.value)) }"
            @click.stop="toggle(option.value)"
          >
            <span class="sp-filter-check">
              <CheckOutlined v-if="selectedSet.has(String(option.value))" />
            </span>
            <span class="sp-filter-text">{{ option.label }}</span>
          </div>
          <div v-if="filteredOptions.length === 0" class="sp-filter-empty">
            {{ keyword.trim() ? '没有匹配的选项' : emptyText }}
          </div>
        </div>

        <div class="sp-filter-foot">
          <span class="sp-filter-count">
            {{ type === 'date' ? (dateStart && dateEnd ? '已选择日期' : '未选择日期') : `已选 ${draft.length} 项` }}
          </span>
          <div class="sp-filter-actions">
            <a-button size="small" type="text" @click.stop="clear">清空</a-button>
            <a-button size="small" type="primary" @click.stop="apply">确定</a-button>
          </div>
        </div>
      </div>
    </template>

    <span class="sp-filter-label">
      {{ label }}
      <DownOutlined class="sp-filter-arrow" />
    </span>
  </a-popover>
</template>

<style scoped>
.sp-filter-label {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 30px;
  padding: 0 7px;
  border-radius: 3px;
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.sp-filter-label:hover {
  color: #007aff;
  background: transparent;
}

.sp-filter-arrow {
  font-size: 9px;
  color: #8f959e;
  transform: translateY(1px);
}

.sp-filter-panel {
  width: 278px;
  padding: 4px 0 0;
  color: #1f2329;
}

.sp-filter-search {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  margin: 0 8px 8px;
  padding: 0 9px;
  border: 1px solid #e5e6eb;
  border-radius: 3px;
  color: #8f959e;
  font-size: 12px;
}

.sp-filter-search input {
  width: 100%;
  border: 0;
  outline: none;
  font-size: 13px;
  color: #1f2329;
  background: transparent;
}

.sp-filter-options {
  max-height: 238px;
  overflow-y: auto;
  padding: 0 4px;
}

.sp-filter-option {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 8px;
  border-radius: 3px;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
}

.sp-filter-option:hover {
  background: #f5f8ff;
}

.sp-filter-check {
  width: 15px;
  height: 15px;
  flex: 0 0 15px;
  border: 1px solid #d0d5dd;
  border-radius: 2px;
  color: #fff;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sp-filter-option.selected {
  color: #007aff;
}

.sp-filter-option.selected .sp-filter-check {
  border-color: #007aff;
  background: #007aff;
}

.sp-filter-text {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sp-filter-empty {
  padding: 26px 0;
  text-align: center;
  color: #8f959e;
  font-size: 12px;
}

.sp-filter-date {
  padding: 8px 12px 4px;
}

.sp-date-line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sp-date-input {
  width: 112px;
  height: 30px;
  padding: 0 6px;
  border: 1px solid #d9d9d9;
  border-radius: 3px;
  color: #1f2329;
  font-size: 12px;
  outline: none;
}

.sp-date-input:focus {
  border-color: #007aff;
}

.sp-date-split {
  color: #8f959e;
  font-size: 12px;
}

.sp-filter-tip {
  margin-top: 8px;
  color: #8f959e;
  font-size: 11px;
}

.sp-filter-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  padding: 8px 10px 4px;
  border-top: 1px solid #f0f1f3;
}

.sp-filter-count {
  color: #8f959e;
  font-size: 12px;
}

.sp-filter-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
