<template>
  <div class="page-container">
    <van-nav-bar title="系统管理" left-arrow @click-left="goBack" fixed placeholder />

    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="引擎管理" name="engines">
        <Engines />
      </van-tab>
      <van-tab title="目录管理" name="directories">
        <Directories />
      </van-tab>
      <van-tab title="用户管理" name="users">
        <Users />
      </van-tab>
      <van-tab title="系统设置" name="settings">
        <Settings />
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Engines from './admin/Engines.vue'
import Directories from './admin/Directories.vue'
import Users from './admin/Users.vue'
import Settings from './admin/Settings.vue'

const router = useRouter()
const activeTab = ref('engines')

function goBack() {
  router.back()
}
</script>

<style scoped>
:deep(.van-tabs__wrap) {
  border-bottom: 1px solid #ebedf0;
}
</style>
