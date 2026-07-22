<template>
  <div class="admin-section">
    <van-cell-group inset title="媒体">
      <van-cell title="缩略图生成" label="关闭后同步时不再生成图片缩略图">
        <template #right-icon>
          <van-switch v-model="settings.thumbnailEnabled" size="20" @change="saveSettings" />
        </template>
      </van-cell>
    </van-cell-group>

    <van-cell-group inset title="同步过滤" class="group-gap">
      <van-cell title="忽略点目录" label="不同步以 . 开头的文件夹（如 .git、.Trash）">
        <template #right-icon>
          <van-switch v-model="settings.ignoreDotDirs" size="20" @change="saveSettings" />
        </template>
      </van-cell>

      <div class="ext-block">
        <div class="ext-header">
          <div>
            <div class="ext-title">忽略文件后缀</div>
            <div class="ext-desc">匹配后缀的文件不同步入库（不含点，如 nfo）</div>
          </div>
        </div>

        <div class="ext-tags" v-if="extList.length">
          <van-tag
            v-for="ext in extList"
            :key="ext"
            closeable
            type="primary"
            size="medium"
            class="ext-tag"
            @close="removeExt(ext)"
          >
            .{{ ext }}
          </van-tag>
        </div>
        <div v-else class="ext-empty">暂无忽略后缀，所有文件都会同步</div>

        <div class="ext-input-row">
          <van-field
            v-model="extInput"
            placeholder="输入后缀，如 nfo 或 nfo,url"
            clearable
            @keyup.enter="addExts"
          />
          <van-button type="primary" size="small" class="ext-add-btn" @click="addExts">
            添加
          </van-button>
        </div>

        <div class="ext-presets">
          <span class="preset-label">常用：</span>
          <van-tag
            v-for="p in presets"
            :key="p"
            plain
            type="primary"
            size="medium"
            class="preset-tag"
            @click="addOne(p)"
          >
            .{{ p }}
          </van-tag>
        </div>
      </div>
    </van-cell-group>

    <van-cell-group inset title="存储" class="group-gap">
      <van-cell title="数据目录" :value="settings.dataDir || '-'" />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '@/api'
import { showToast } from 'vant'

const presets = ['nfo', 'url', 'db', 'tmp', 'log', 'ini', 'txt', 'bak']

const settings = ref({
  thumbnailEnabled: true,
  ignoreDotDirs: true,
  ignoreFileExtensions: 'nfo,url,db,tmp,log,ini',
  dataDir: ''
})

const extInput = ref('')

const extList = computed(() => parseExts(settings.value.ignoreFileExtensions))

function parseExts(raw) {
  if (!raw) return []
  const set = new Set()
  String(raw)
    .split(/[,;\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .forEach((s) => {
      const ext = (s.startsWith('.') ? s.slice(1) : s).toLowerCase()
      if (ext) set.add(ext)
    })
  return [...set]
}

function joinExts(list) {
  return list.join(',')
}

async function loadSettings() {
  try {
    const res = await api.get('/api/settings')
    if (res.code === 200) {
      settings.value = {
        thumbnailEnabled: res.data.thumbnailEnabled !== false,
        ignoreDotDirs: res.data.ignoreDotDirs !== false,
        ignoreFileExtensions: res.data.ignoreFileExtensions || '',
        dataDir: res.data.dataDir || ''
      }
    }
  } catch (error) {
    console.error('加载设置失败:', error)
  }
}

async function saveSettings() {
  try {
    const res = await api.put('/api/settings', {
      thumbnailEnabled: settings.value.thumbnailEnabled,
      ignoreDotDirs: settings.value.ignoreDotDirs,
      ignoreFileExtensions: settings.value.ignoreFileExtensions
    })
    if (res.code === 200) {
      showToast(res.message || '设置已保存')
    }
  } catch (error) {
    showToast('保存失败')
  }
}

function addOne(ext) {
  const list = parseExts(settings.value.ignoreFileExtensions)
  const e = (ext.startsWith('.') ? ext.slice(1) : ext).toLowerCase()
  if (!e || list.includes(e)) return
  list.push(e)
  settings.value.ignoreFileExtensions = joinExts(list)
  saveSettings()
}

function addExts() {
  const parts = parseExts(extInput.value)
  if (!parts.length) {
    showToast('请输入后缀')
    return
  }
  const list = parseExts(settings.value.ignoreFileExtensions)
  let changed = false
  parts.forEach((p) => {
    if (!list.includes(p)) {
      list.push(p)
      changed = true
    }
  })
  extInput.value = ''
  if (!changed) {
    showToast('已存在')
    return
  }
  settings.value.ignoreFileExtensions = joinExts(list)
  saveSettings()
}

function removeExt(ext) {
  const list = parseExts(settings.value.ignoreFileExtensions).filter((e) => e !== ext)
  settings.value.ignoreFileExtensions = joinExts(list)
  saveSettings()
}

onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
.admin-section {
  padding: 12px;
  padding-bottom: 24px;
}

.group-gap {
  margin-top: 12px;
}

.ext-block {
  padding: 12px 16px 16px;
  background: #fff;
}

.ext-header {
  margin-bottom: 12px;
}

.ext-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-color, #323233);
}

.ext-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-color-3, #969799);
  line-height: 1.4;
}

.ext-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.ext-tag {
  padding: 4px 8px;
}

.ext-empty {
  font-size: 12px;
  color: var(--text-color-3, #c8c9cc);
  margin-bottom: 12px;
}

.ext-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f7f8fa;
  border-radius: 10px;
  overflow: hidden;
  padding-right: 8px;
}

.ext-input-row :deep(.van-cell) {
  background: transparent;
  padding: 8px 12px;
  flex: 1;
}

.ext-add-btn {
  flex-shrink: 0;
  border-radius: 8px;
}

.ext-presets {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
}

.preset-label {
  font-size: 12px;
  color: var(--text-color-3, #969799);
}

.preset-tag {
  cursor: pointer;
}
</style>
