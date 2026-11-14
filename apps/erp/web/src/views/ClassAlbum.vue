<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import {
  deleteClassPhoto,
  fetchClasses,
  fetchClassPhotos,
  uploadClassPhoto,
  type ClassPhoto,
} from '../api/client'

interface OptionItem {
  label: string
  value: number
}

const classOptions = ref<OptionItem[]>([])
const classId = ref<number | undefined>(undefined)
const photos = ref<ClassPhoto[]>([])
const loading = ref(false)

const currentClassName = computed(
  () => classOptions.value.find((item) => item.value === classId.value)?.label ?? '',
)

function filterOption(input: string, option: { label?: string }) {
  return String(option.label ?? '').toLowerCase().includes(input.toLowerCase())
}

function photoTime(value: string | null | undefined) {
  if (!value) return ''
  const text = String(value)
  return text.length >= 16 ? `${text.slice(0, 10)} ${text.slice(11, 16)}` : text.slice(0, 10)
}

async function loadOptions() {
  try {
    const res = await fetchClasses({ page: 1, size: 500 })
    classOptions.value = res.records.map((item) => ({ label: item.name, value: item.id }))
  } catch {
    // 班级列表加载失败时保留空选项，由查询提示
  }
}

async function loadPhotos() {
  if (!classId.value) {
    photos.value = []
    return
  }
  loading.value = true
  try {
    photos.value = await fetchClassPhotos(classId.value)
  } catch (e) {
    photos.value = []
    message.error(e instanceof Error ? e.message : '班级相册加载失败')
  } finally {
    loading.value = false
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function handleUpload(options: any) {
  if (!classId.value) {
    message.warning('请先选择班级')
    options?.onError?.(new Error('未选择班级'))
    return
  }
  const file = options?.file as File | undefined
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件')
    options.onError?.(new Error('格式不支持'))
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.warning('单张照片不能超过 10MB')
    options.onError?.(new Error('文件过大'))
    return
  }
  try {
    const photo = await uploadClassPhoto(classId.value, file)
    photos.value.unshift(photo)
    options.onSuccess?.(photo)
    message.success('照片已上传')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '照片上传失败')
    options.onError?.(e as Error)
  }
}

function removePhoto(photo: ClassPhoto) {
  Modal.confirm({
    title: '删除照片',
    content: '删除后不可恢复，确认删除这张照片？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteClassPhoto(photo.id)
        photos.value = photos.value.filter((item) => item.id !== photo.id)
        message.success('照片已删除')
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    },
  })
}

onMounted(async () => {
  await loadOptions()
  if (classOptions.value.length) {
    classId.value = classOptions.value[0].value
    await loadPhotos()
  }
})
</script>

<template>
  <div class="album-page">
    <div class="toolbar">
      <span class="filter-label">班级</span>
      <a-select
        v-model:value="classId"
        size="small"
        show-search
        style="width: 220px"
        placeholder="请选择班级"
        :options="classOptions"
        :filter-option="filterOption"
        @change="loadPhotos"
      />
      <a-upload
        v-if="classId"
        :show-upload-list="false"
        :custom-request="handleUpload"
        accept="image/*"
        multiple
      >
        <a-button type="primary" size="small">上传照片</a-button>
      </a-upload>
      <span class="album-hint">支持 JPG / PNG / WebP / GIF，单张不超过 10MB</span>
      <span class="toolbar-spacer" />
      <a-button size="small" :disabled="!classId" @click="loadPhotos">
        <ReloadOutlined />刷新
      </a-button>
    </div>

    <div class="summary-line">
      {{ currentClassName ? `${currentClassName}：共 ${photos.length} 张照片` : '请选择要维护相册的班级' }}
    </div>

    <a-spin :spinning="loading">
      <div v-if="photos.length" class="album-grid">
        <div v-for="photo in photos" :key="photo.id" class="album-item">
          <a-image :src="photo.url" class="album-image" />
          <div class="album-meta">
            <span class="album-uploader" :title="photo.uploader || ''">{{ photo.uploader || '未知' }}</span>
            <span class="album-time">{{ photoTime(photo.createdAt) }}</span>
            <a class="album-delete" @click="removePhoto(photo)">删除</a>
          </div>
        </div>
      </div>
      <a-empty v-else :description="classId ? '暂无照片，上传班级活动的精彩瞬间' : '请先选择班级'" />
    </a-spin>
  </div>
</template>

<style scoped>
.album-page {
  min-height: 100%;
  background: #fff;
  padding: 0 24px 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px 0 10px;
  font-size: 13px;
}

.filter-label {
  color: var(--text-2);
}

.toolbar-spacer {
  flex: 1;
}

.album-hint {
  font-size: 12px;
  color: var(--text-2);
}

.summary-line {
  padding: 2px 0 12px;
  font-size: 13px;
  color: var(--text-2);
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
}

.album-item {
  border: 1px solid #eef0f3;
  border-radius: 6px;
  overflow: hidden;
  background: #fafbfc;
}

.album-image {
  display: block;
  width: 100%;
  height: 140px;
  object-fit: cover;
}

.album-image :deep(img) {
  width: 100%;
  height: 140px;
  object-fit: cover;
}

.album-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  font-size: 12px;
  color: var(--text-2);
}

.album-uploader {
  max-width: 70px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.album-time {
  flex: 1;
}

.album-delete {
  color: #d4380d;
}

.album-delete:hover {
  color: #ff4d4f;
}
</style>
