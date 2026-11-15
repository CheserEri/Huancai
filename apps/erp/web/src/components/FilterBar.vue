<script setup lang="ts">
/**
 * 列表页统一筛选工具栏。
 * 左侧筛选项区自适应换行，右侧搜索框与查询/重置按钮始终留在第一行；
 * 放不下时优先把左侧筛选项折到第二行。
 */
interface Props {
  /** 是否显示右侧的查询/重置按钮 */
  actions?: boolean
  searchText?: string
  resetText?: string
}

withDefaults(defineProps<Props>(), {
  actions: true,
  searchText: '查询',
  resetText: '重置',
})

defineEmits<{ (e: 'search'): void; (e: 'reset'): void }>()
</script>

<template>
  <div class="filter-bar">
    <div class="filter-fields">
      <slot />
    </div>
    <div class="filter-actions">
      <slot name="search" />
      <template v-if="actions">
        <a-button type="primary" class="filter-btn" @click="$emit('search')">{{ searchText }}</a-button>
        <a-button class="filter-btn" @click="$emit('reset')">{{ resetText }}</a-button>
      </template>
    </div>
  </div>
</template>
