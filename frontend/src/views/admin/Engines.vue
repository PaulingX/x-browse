<template>
  <div class="admin-section">
    <div class="section-header">
      <span>Alist 存储引擎</span>
      <van-button type="primary" size="small" @click="showAdd = true">
        <van-icon name="plus" /> 添加
      </van-button>
    </div>

    <van-cell-group inset>
      <van-cell
        v-for="engine in engines"
        :key="engine.id"
        :title="engine.remark || '未命名引擎'"
        :label="engine.url"
        is-link
        @click="editEngine(engine)"
      >
        <template #right-icon>
          <van-button
            type="danger"
            size="mini"
            plain
            @click.stop="deleteEngine(engine)"
          >
            删除
          </van-button>
        </template>
      </van-cell>
    </van-cell-group>

    <div v-if="engines.length === 0" class="empty-state">
      <p>暂无存储引擎</p>
    </div>

    <!-- 添加/编辑引擎弹窗 -->
    <van-popup v-model:show="showAdd" position="bottom" round>
      <div class="popup-content">
        <h3>{{ editingEngine ? '编辑引擎' : '添加引擎' }}</h3>
        <van-form @submit="onSubmit">
          <van-cell-group inset>
            <van-field
              v-model="form.remark"
              label="备注名称"
              placeholder="如：我的Alist"
            />
            <van-field
              v-model="form.url"
              label="Alist 地址"
              placeholder="http://localhost:5244"
              :rules="[{ required: true, message: '请输入Alist地址' }]"
            />
            <van-field
              v-model="form.token"
              label="访问令牌"
              placeholder="请输入访问令牌"
              type="password"
              :rules="[{ required: true, message: '请输入访问令牌' }]"
            />
          </van-cell-group>
          <div class="btn-group">
            <van-button
              type="default"
              round
              @click="testConnection"
              :loading="testing"
            >
              测试连接
            </van-button>
            <van-button
              type="primary"
              round
              native-type="submit"
              :loading="submitting"
            >
              保存
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'
import { showToast, showConfirmDialog } from 'vant'

const engines = ref([])
const showAdd = ref(false)
const editingEngine = ref(null)
const testing = ref(false)
const submitting = ref(false)

const form = ref({
  remark: '',
  url: '',
  token: ''
})

// 加载引擎列表
async function loadEngines() {
  try {
    const res = await api.get('/api/engines')
    if (res.code === 200) {
      engines.value = res.data
    }
  } catch (error) {
    console.error('加载引擎列表失败:', error)
  }
}

// 编辑引擎
function editEngine(engine) {
  editingEngine.value = engine
  form.value = {
    remark: engine.remark || '',
    url: engine.url,
    token: ''
  }
  showAdd.value = true
}

// 删除引擎
async function deleteEngine(engine) {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除引擎 "${engine.remark || engine.url}" 吗？`
    })
    const res = await api.delete(`/api/engines/${engine.id}`)
    if (res.code === 200) {
      showToast('删除成功')
      loadEngines()
    }
  } catch (error) {
    if (error !== 'cancel') {
      showToast('删除失败')
    }
  }
}

// 测试连接
async function testConnection() {
  testing.value = true
  try {
    const res = await api.post('/api/engines/test', form.value)
    if (res.code === 200 && res.data) {
      showToast('连接成功')
    } else {
      showToast('连接失败')
    }
  } catch (error) {
    showToast('连接失败')
  } finally {
    testing.value = false
  }
}

// 提交表单
async function onSubmit() {
  submitting.value = true
  try {
    let res
    if (editingEngine.value) {
      res = await api.put(`/api/engines/${editingEngine.value.id}`, form.value)
    } else {
      res = await api.post('/api/engines', form.value)
    }
    if (res.code === 200) {
      showToast(editingEngine.value ? '更新成功' : '添加成功')
      showAdd.value = false
      editingEngine.value = null
      form.value = { remark: '', url: '', token: '' }
      loadEngines()
    }
  } catch (error) {
    showToast('操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadEngines()
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

.popup-content {
  padding: 20px;
  max-height: 80vh;
  overflow-y: auto;
}

.popup-content h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  text-align: center;
}

.btn-group {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  padding: 0 16px;
}

.btn-group .van-button {
  flex: 1;
}
</style>
