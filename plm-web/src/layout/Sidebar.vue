<script setup lang="ts">
import { computed, resolveDynamicComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { sidebarMenus } from '@/mock/data'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const visibleMenus = computed(() =>
  sidebarMenus
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => userStore.hasPermission(item.permission))
    }))
    .filter((group) => group.items.length)
)

function navigate(path: string) {
  router.push(path)
}
</script>

<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': appStore.sidebarCollapsed }">
    <div class="sidebar__brand">
      <div class="sidebar__logo">YW</div>
      <div v-if="!appStore.sidebarCollapsed" class="sidebar__title">
        <strong>Yuewei PLM</strong>
        <span>产品研发协同</span>
      </div>
    </div>

    <div class="sidebar__menus">
      <div v-for="group in visibleMenus" :key="group.title" class="sidebar__group">
        <div v-if="!appStore.sidebarCollapsed" class="sidebar__group-title">{{ group.title }}</div>
        <button
          v-for="item in group.items"
          :key="item.path"
          class="sidebar__item"
          :class="{ 'is-active': route.path === item.path }"
          type="button"
          @click="navigate(item.path)"
        >
          <el-icon><component :is="resolveDynamicComponent(item.icon)" /></el-icon>
          <span v-if="!appStore.sidebarCollapsed">{{ item.title }}</span>
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  width: 220px;
  min-width: 220px;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  background: linear-gradient(180deg, var(--plm-color-sidebar) 0%, var(--plm-color-sidebar-accent) 100%);
  color: #eef4ff;
  transition: width 0.2s ease, min-width 0.2s ease;
}

.sidebar--collapsed {
  width: 72px;
  min-width: 72px;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 16px;
}

.sidebar__logo {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(64, 158, 255, 0.2);
  font-weight: 700;
}

.sidebar__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: rgba(238, 244, 255, 0.8);
}

.sidebar__group {
  padding: 10px 12px 0;
}

.sidebar__group-title {
  margin-bottom: 10px;
  padding: 0 8px;
  color: rgba(238, 244, 255, 0.58);
  font-size: 12px;
}

.sidebar__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-bottom: 6px;
  padding: 11px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.sidebar__item:hover,
.sidebar__item.is-active {
  background: rgba(255, 255, 255, 0.1);
}

.sidebar--collapsed .sidebar__item {
  justify-content: center;
}
</style>
