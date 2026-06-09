<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Expand, Fold } from '@element-plus/icons-vue'

import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const breadcrumbItems = computed(() =>
  route.matched
    .filter((item) => item.meta?.title)
    .map((item) => ({ path: item.path, title: String(item.meta.title) }))
)

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <header class="navbar">
    <div class="navbar__left">
      <el-button circle :icon="appStore.sidebarCollapsed ? Expand : Fold" @click="appStore.toggleSidebar()" />
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="item in breadcrumbItems" :key="item.path">
          {{ item.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="navbar__right">
      <div class="navbar__user">
        <strong>{{ userStore.profile?.userName || '--' }}</strong>
        <span>{{ userStore.profile?.roleName || '--' }}</span>
      </div>
      <el-button text @click="handleLogout">退出</el-button>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--plm-color-border-light);
  background: var(--plm-color-navbar);
  backdrop-filter: blur(10px);
}

.navbar__left,
.navbar__right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.navbar__user {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: right;
}

.navbar__user span {
  color: var(--plm-color-text-secondary);
  font-size: 12px;
}
</style>
