<template>
  <div class="admin-section">
    <div class="section-header">
      <span>浏览目录</span>
      <van-button type="primary" size="small" @click="openAdd">
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
              <van-tag :type="dir.syncMode === 'NONE' ? 'default' : 'success'" size="small">
                {{ dir.syncDesc || getSyncDesc(dir) }}
              </van-tag>
            </div>
            <div class="sync-time" v-if="dir.lastSyncTime || dir.nextSyncTime">
              <span v-if="dir.lastSyncTime">上次：{{ formatTime(dir.lastSyncTime) }}</span>
              <span v-if="dir.nextSyncTime">下次：{{ formatTime(dir.nextSyncTime) }}</span>
            </div>
          </div>
        </template>
        <template #right-icon>
          <div class="dir-actions">
            <van-button
              type="primary"
              size="mini"
              plain
              :loading="syncingId === dir.id"
              @click.stop="syncNow(dir)"
            >
              同步
            </van-button>
            <van-button
              type="danger"
              size="mini"
              plain
              @click.stop="deleteDir(dir)"
            >
              删除
            </van-button>
          </div>
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
            <div class="sync-card">
              <div class="sync-card-title">同步策略</div>
              <van-radio-group v-model="form.syncMode" direction="horizontal" class="sync-mode-group">
                <van-radio name="INTERVAL">间隔</van-radio>
                <van-radio name="CRON">Cron</van-radio>
                <van-radio name="NONE">不同步</van-radio>
              </van-radio-group>

              <template v-if="form.syncMode === 'INTERVAL'">
                <div class="interval-row">
                  <van-field
                    v-model.number="form.syncIntervalValue"
                    label="每隔"
                    type="digit"
                    placeholder="数值"
                    :rules="[{ validator: validateInterval, message: '请输入大于 0 的间隔' }]"
                  />
                  <van-field label="单位">
                    <template #input>
                      <div class="unit-tabs">
                        <span
                          v-for="unit in intervalUnits"
                          :key="unit.value"
                          class="unit-tab"
                          :class="{ active: form.syncIntervalUnit === unit.value }"
                          @click="form.syncIntervalUnit = unit.value"
                        >{{ unit.label }}</span>
                      </div>
                    </template>
                  </van-field>
                </div>
              </template>

              <van-field
                v-if="form.syncMode === 'CRON'"
                v-model="form.syncCron"
                label="Cron"
                placeholder="如：0 0 */6 * * *"
                :rules="[{ validator: validateCron, message: '请输入 6 位 Cron 表达式' }]"
              />
              <div class="sync-help">
                {{ syncHelpText }}
              </div>
            </div>
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
import { ref, computed, onMounted } from 'vue'
import api from '@/api'
import { showToast, showConfirmDialog } from 'vant'

const directories = ref([])
const engines = ref([])
const showAdd = ref(false)
const showEnginePicker = ref(false)
const showDirBrowser = ref(false)
const editingDir = ref(null)
const submitting = ref(false)
const syncingId = ref(null)

const intervalUnits = [
  { label: '分钟', value: 'MINUTE' },
  { label: '小时', value: 'HOUR' },
  { label: '天', value: 'DAY' },
  { label: '月', value: 'MONTH' }
]

const form = ref({
  engineId: null,
  path: '',
  name: '',
  thumbnailEnabled: true,
  syncMode: 'INTERVAL',
  syncIntervalValue: 5,
  syncIntervalUnit: 'MINUTE',
  syncCron: ''
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

const syncHelpText = computed(() => {
  if (form.value.syncMode === 'NONE') return '该目录只在手动点击同步时更新。'
  if (form.value.syncMode === 'CRON') return 'Cron 使用 6 位格式：秒 分 时 日 月 周，例如每 6 小时：0 0 */6 * * *'
  const unit = intervalUnits.find(u => u.value === form.value.syncIntervalUnit)?.label || '分钟'
  return `该目录将按独立任务每 ${form.value.syncIntervalValue || 0} ${unit}同步一次。`
})

function defaultForm() {
  return {
    engineId: engines.value.length === 1 ? engines.value[0].id : null,
    path: '',
    name: '',
    thumbnailEnabled: true,
    syncMode: 'INTERVAL',
    syncIntervalValue: 5,
    syncIntervalUnit: 'MINUTE',
    syncCron: ''
  }
}

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

// 打开添加弹窗：仅一个引擎时默认选中
function openAdd() {
  editingDir.value = null
  form.value = defaultForm()
  showAdd.value = true
}

function validateInterval(value) {
  if (form.value.syncMode !== 'INTERVAL') return true
  return Number(value) > 0
}

function validateCron(value) {
  if (form.value.syncMode !== 'CRON') return true
  return typeof value === 'string' && value.trim().split(/\s+/).length === 6
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function getSyncDesc(dir) {
  if (!dir || dir.syncMode === 'NONE') return '不同步'
  if (dir.syncMode === 'CRON') return `Cron: ${dir.syncCron || '-'}`
  const unitMap = { MINUTE: '分钟', HOUR: '小时', DAY: '天', MONTH: '月' }
  return `每 ${dir.syncIntervalValue || 5} ${unitMap[dir.syncIntervalUnit] || '分钟'}`
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
      params: { engineId: form.value.engineId, path, refresh: true, page: 1, perPage: 500 }
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
    thumbnailEnabled: dir.thumbnailEnabled,
    syncMode: dir.syncMode || 'INTERVAL',
    syncIntervalValue: dir.syncIntervalValue || 5,
    syncIntervalUnit: dir.syncIntervalUnit || 'MINUTE',
    syncCron: dir.syncCron || ''
  }
  showAdd.value = true
}

// 立即同步单个浏览目录
async function syncNow(dir) {
  syncingId.value = dir.id
  try {
    const res = await api.post(`/api/directories/${dir.id}/sync`)
    if (res.code === 200) {
      showToast('同步完成')
      loadData()
    }
  } catch (error) {
    showToast('同步失败')
  } finally {
    syncingId.value = null
  }
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
      form.value = defaultForm()
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
  flex-wrap: wrap;
  gap: 4px;
}

.sync-time {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
  color: var(--text-color-3);
}

.dir-actions {
  display: flex;
  gap: 6px;
  align-items: center;
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

.sync-card {
  margin: 12px 0 4px;
  padding: 14px 12px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(25, 137, 250, 0.08), rgba(7, 193, 96, 0.06));
  border: 1px solid rgba(25, 137, 250, 0.12);
}

.sync-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 12px;
}

.sync-mode-group {
  margin-bottom: 10px;
}

.interval-row {
  overflow: hidden;
  border-radius: 10px;
  background: #fff;
}

.unit-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.unit-tab {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-color-2);
  background: #f2f3f5;
  cursor: pointer;
}

.unit-tab.active {
  color: #fff;
  background: var(--primary-color);
}

.sync-help {
  margin-top: 10px;
  color: var(--text-color-3);
  font-size: 12px;
  line-height: 1.5;
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
