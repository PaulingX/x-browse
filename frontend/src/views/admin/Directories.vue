<template>
  <div class="admin-section">
    <div class="section-header">
      <span>浏览目录</span>
      <van-button type="primary" size="small" @click="showAdd = true">
        <van-icon name="plus" /> 添加
      </van-button>
    </div>

    <van-cell-group inset>
      <van-cell
        v-for="dir in directories"
        :key="dir.id"
        :title="dir.name || dir.path"
        :label="getEngineName(dir.engineId) + ' - ' + dir.path"
        is-link
        @click="editDir(dir)"
      >
        <template #label>
          <div class="dir-info">
            <span>{{ getEngineName(dir.engineId) }} - {{ dir.path }}</span>
            <div class="dir-tags">
              <van-tag v-if="dir.thumbnailEnabled" type="primary" size="small">缩略图</van-tag>
            </div>
          </div>
        </template>
        <template #right-icon>
          <van-button
            type="danger"
            size="mini"
            plain
            @click.stop="deleteDir(dir)"
          >
            删除
          </van-button>
        </template>
      </van-cell>
    </van-cell-group>

    <div v-if="directories.length === 0" class="empty-state">
      <p>暂无浏览目录</p>
    </div>

    <!-- 添加/编辑目录弹窗 -->
    <van-popup v-model:show="showAdd" position="bottom" round :style="{ height: '80%' }">
      <div class="popup-content">
        <h3>{{ editingDir ? '编辑目录' : '添加目录' }}</h3>
        <van-form @submit="onSubmit">
          <van-cell-group inset>
            <van-field
              :model-value="getEngineName(form.engineId)"
              label="选择引擎"
              placeholder="请选择Alist引擎"
              is-link
              readonly
              :rules="[{ required: true, message: '请选择引擎' }]"
              @click="showEnginePicker = true"
            />
            <van-field
              :model-value="form.path"
              label="目录路径"
              placeholder="点击选择目录"
              readonly
              is-link
              :rules="[{ required: true, message: '请选择目录' }]"
              @click="openDirBrowser"
            />
            <van-field
              v-model="form.name"
              label="显示名称"
              placeholder="如：我的相册"
            />
            <van-field label="缩略图">
              <template #input>
                <van-switch v-model="form.thumbnailEnabled" size="20" />
              </template>
            </van-field>
          </van-cell-group>
          <div class="submit-btn">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              保存
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 引擎选择器 -->
    <van-popup v-model:show="showEnginePicker" position="bottom" round>
      <van-picker
        :columns="engineOptions"
        @confirm="onEngineSelect"
        @cancel="showEnginePicker = false"
      />
    </van-popup>

    <!-- 目录选择器 -->
    <van-popup v-model:show="showDirBrowser" position="bottom" round :style="{ height: '70%' }">
      <div class="dir-browser">
        <div class="dir-browser-header">
          <span>选择目录</span>
          <van-button size="small" @click="showDirBrowser = false">取消</van-button>
        </div>
        <div class="dir-browser-path">
          <span v-for="(part, index) in pathParts" :key="index">
            <span class="path-link" @click="navigateTo(index)">{{ part || '根目录' }}</span>
            <span v-if="index < pathParts.length - 1" class="path-sep">/</span>
          </span>
        </div>
        <div class="dir-browser-list" v-loading="dirLoading">
          <van-cell
            v-if="currentPath !== '/'"
            title=".."
            label="返回上级"
            is-link
            @click="goBack"
          >
            <template #icon>
              <van-icon name="arrow-left" />
            </template>
          </van-cell>
          <van-cell
            v-for="item in dirItems"
            :key="item.name"
            :title="item.name"
            is-link
            @click="enterDir(item)"
          >
            <template #icon>
              <van-icon name="folder" />
            </template>
          </van-cell>
          <div v-if="!dirLoading && dirItems.length === 0 && currentPath === '/'" class="empty-state">
            <p>该引擎无目录</p>
          </div>
        </div>
        <div class="dir-browser-footer">
          <van-button
            block
            type="primary"
            @click="confirmDir"
            :disabled="!currentPath"
          >
            选择当前目录: {{ currentPath }}
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import api from '@/api'
import { showToast, showConfirmDialog } from 'vant'

const directories = ref([])
const engines = ref([])
const showAdd = ref(false)
const showEnginePicker = ref(false)
const showDirBrowser = ref(false)
const editingDir = ref(null)
const submitting = ref(false)

const form = ref({
  engineId: null,
  path: '',
  name: '',
  thumbnailEnabled: true
})

const engineOptions = computed(() => {
  return engines.value.map((e) => ({
    text: e.remark || e.url,
    value: e.id
  }))
})

// 目录浏览器状态
const dirLoading = ref(false)
const currentPath = ref('/')
const dirItems = ref([])

const pathParts = computed(() => {
  return currentPath.value.split('/').filter(Boolean)
})

function getEngineName(engineId) {
  const engine = engines.value.find((e) => e.id === engineId)
  return engine?.remark || engine?.url || '未知引擎'
}

// 加载数据
async function loadData() {
  try {
    const [enginesRes, dirsRes] = await Promise.all([
      api.get('/api/engines'),
      api.get('/api/directories')
    ])
    if (enginesRes.code === 200) engines.value = enginesRes.data
    if (dirsRes.code === 200) directories.value = dirsRes.data
  } catch (error) {
    console.error('加载数据失败:', error)
  }
}

// 选择引擎
function onEngineSelect({ selectedOptions }) {
  form.value.engineId = selectedOptions[0]?.value
  showEnginePicker.value = false
}

// 打开目录浏览器
function openDirBrowser() {
  if (!form.value.engineId) {
    showToast('请先选择引擎')
    return
  }
  currentPath.value = '/'
  dirItems.value = []
  showDirBrowser.value = true
  loadDirs('/')
}

// 加载目录列表
async function loadDirs(path) {
  if (!form.value.engineId) return
  dirLoading.value = true
  try {
    const res = await api.get('/api/files/list', {
      params: { engineId: form.value.engineId, path, refresh: false, page: 1, perPage: 500 }
    })
    if (res.code === 200) {
      dirItems.value = (res.data || []).filter(item => item.isDir)
    } else {
      dirItems.value = []
    }
  } catch (error) {
    dirItems.value = []
    showToast('加载目录失败')
  } finally {
    dirLoading.value = false
  }
}

// 进入目录
function enterDir(item) {
  const newPath = currentPath.value === '/'
    ? '/' + item.name
    : currentPath.value + '/' + item.name
  currentPath.value = newPath
  loadDirs(newPath)
}

// 返回上级
function goBack() {
  const parts = currentPath.value.split('/').filter(Boolean)
  parts.pop()
  currentPath.value = parts.length ? '/' + parts.join('/') : '/'
  loadDirs(currentPath.value)
}

// 点击面包屑导航
function navigateTo(index) {
  const parts = currentPath.value.split('/').filter(Boolean)
  currentPath.value = '/' + parts.slice(0, index + 1).join('/')
  loadDirs(currentPath.value)
}

// 确认选择目录
function confirmDir() {
  form.value.path = currentPath.value
  showDirBrowser.value = false
}

// 编辑目录
function editDir(dir) {
  editingDir.value = dir
  form.value = {
    engineId: dir.engineId,
    path: dir.path,
    name: dir.name || '',
    thumbnailEnabled: dir.thumbnailEnabled
  }
  showAdd.value = true
}

// 删除目录
async function deleteDir(dir) {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除目录 "${dir.name || dir.path}" 吗？`
    })
    const res = await api.delete(`/api/directories/${dir.id}`)
    if (res.code === 200) {
      showToast('删除成功')
      loadData()
    }
  } catch (error) {
    if (error !== 'cancel') {
      showToast('删除失败')
    }
  }
}

// 提交表单
async function onSubmit() {
  submitting.value = true
  try {
    let res
    if (editingDir.value) {
      res = await api.put(`/api/directories/${editingDir.value.id}`, form.value)
    } else {
      res = await api.post('/api/directories', form.value)
    }
    if (res.code === 200) {
      showToast(editingDir.value ? '更新成功' : '添加成功')
      showAdd.value = false
      editingDir.value = null
      form.value = { engineId: null, path: '', name: '', thumbnailEnabled: true }
      loadData()
    }
  } catch (error) {
    showToast('操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.admin-section {
  padding: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  font-size: 16px;
  font-weight: 500;
}

.dir-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dir-tags {
  display: flex;
  gap: 4px;
}

.popup-content {
  padding: 20px;
  height: 100%;
  overflow-y: auto;
}

.popup-content h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  text-align: center;
}

.submit-btn {
  margin-top: 20px;
  padding: 0 16px;
}

/* 目录浏览器样式 */
.dir-browser {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.dir-browser-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  font-size: 16px;
  font-weight: 500;
  border-bottom: 1px solid var(--border-color);
}

.dir-browser-path {
  padding: 12px 16px;
  font-size: 14px;
  color: var(--text-color-2);
  background: var(--bg-color);
  overflow-x: auto;
  white-space: nowrap;
}

.path-link {
  color: var(--primary-color);
  cursor: pointer;
}

.path-link:hover {
  text-decoration: underline;
}

.path-sep {
  margin: 0 4px;
  color: var(--text-color-3);
}

.dir-browser-list {
  flex: 1;
  overflow-y: auto;
}

.dir-browser-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
}

.empty-state {
  text-align: center;
  padding: 40px 0;
  color: var(--text-color-3);
}
</style>
