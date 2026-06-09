<script setup lang="ts">
import { computed, reactive, resolveDynamicComponent } from 'vue'
import { ArrowDown, ArrowLeftBold, ArrowRightBold, ArrowUp } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'

import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import type { MenuGroup } from '@/types/common'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const menuGroups: MenuGroup[] = [
  {
    title: '工作台',
    items: [{ path: '/dashboard', title: '首页总览', icon: 'Odometer', permission: 'dashboard:view' }]
  },
  {
    title: '基础资料管理',
    items: [
      { path: '/files', title: '文件管理', icon: 'FolderOpened', permission: 'product:view' },
      { path: '/production-orders', title: '测试管理', icon: 'Finished', permission: 'production-order:view' },
      { path: '/bom', title: 'BOM 管理', icon: 'List', permission: 'product:view' },
      { path: '/suppliers', title: '供应商管理', icon: 'Van', permission: 'supplier:view' },
      { path: '/processes', title: '工艺路线', icon: 'Connection', permission: 'process:view' },
      { path: '/inventories', title: '物料库存', icon: 'Box', permission: 'inventory:view' },
      { path: '/orders', title: '需求订单', icon: 'Document', permission: 'order:view' }
    ]
  },
  {
    title: 'SKU 管理',
    items: [{ path: '/sku-view', title: 'SKU 视图', icon: 'Tickets', permission: 'product:view' }]
  },
  {
    title: '产品管理',
    items: [
      { path: '/products?lifecycle=initiation', title: '立项中', icon: 'Flag', permission: 'product:view' },
      { path: '/products?lifecycle=sampling', title: '打样中', icon: 'Promotion', permission: 'product:view' },
      { path: '/products?lifecycle=tooling', title: '模具阶段', icon: 'Tools', permission: 'product:view' },
      { path: '/products?lifecycle=semi_finished', title: '半成品', icon: 'Box', permission: 'product:view' },
      { path: '/products?lifecycle=finished', title: '成品阶段', icon: 'Goods', permission: 'product:view' },
      { path: '/products?lifecycle=released', title: '已发布', icon: 'CircleCheck', permission: 'product:view' }
    ]
  },
  {
    title: '项目管理',
    items: [
      { path: '/projects?tab=in_progress', title: '进行中', icon: 'Management', permission: 'project:view' },
      { path: '/projects?tab=completed', title: '已完成', icon: 'Select', permission: 'project:view' },
      { path: '/projects?tab=abandoned', title: '已放弃', icon: 'RemoveFilled', permission: 'project:view' }
    ]
  },
  {
    title: '报表',
    items: [{ path: '/reports', title: '报表中心', icon: 'DataAnalysis', permission: 'report:view' }]
  },
  {
    title: '系统管理',
    items: [
      { path: '/approval-tasks', title: '审批中心', icon: 'Checked', permission: 'approval:view' },
      { path: '/system/users', title: '用户管理', icon: 'Avatar', permission: 'admin:user' },
      { path: '/system/roles', title: '角色管理', icon: 'Key', permission: 'admin:role' },
      { path: '/system/dicts', title: '字典管理', icon: 'Collection', permission: 'admin:dict' },
      { path: '/system/import', title: '数据导入', icon: 'Upload', permission: 'admin:import' }
    ]
  }
]

const groupState = reactive<Record<string, boolean>>({
  工作台: true,
  基础资料管理: true,
  'SKU 管理': true,
  产品管理: true,
  项目管理: true,
  报表: false,
  系统管理: false
})

const visibleMenus = computed(() =>
  menuGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => userStore.hasPermission(item.permission))
    }))
    .filter((group) => group.items.length)
)

function normalizePath(path: string) {
  return path.replace(/\/+$/, '')
}

function navigate(path: string) {
  router.push(path)
}

function isActive(path: string) {
  return normalizePath(route.fullPath) === normalizePath(path) || normalizePath(route.path) === normalizePath(path)
}

function groupHasActiveItem(group: MenuGroup) {
  return group.items.some((item) => isActive(item.path))
}

function isGroupOpen(group: MenuGroup) {
  if (appStore.sidebarCollapsed) return false
  return groupState[group.title] ?? true
}

function toggleGroup(title: string) {
  if (appStore.sidebarCollapsed) return
  groupState[title] = !groupState[title]
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
      <section v-for="group in visibleMenus" :key="group.title" class="sidebar__group">
        <button
          class="sidebar__group-trigger"
          :class="{ 'is-open': isGroupOpen(group), 'is-active': groupHasActiveItem(group) }"
          type="button"
          @click="toggleGroup(group.title)"
        >
          <span v-if="!appStore.sidebarCollapsed" class="sidebar__group-label">{{ group.title }}</span>
          <el-icon v-else class="sidebar__group-icon">
            <component :is="resolveDynamicComponent(group.items[0].icon)" />
          </el-icon>
          <el-icon v-if="!appStore.sidebarCollapsed" class="sidebar__group-arrow">
            <component :is="isGroupOpen(group) ? ArrowUp : ArrowDown" />
          </el-icon>
        </button>

        <div v-if="isGroupOpen(group)" class="sidebar__group-items">
          <button
            v-for="item in group.items"
            :key="item.path"
            class="sidebar__item"
            :class="{ 'is-active': isActive(item.path) }"
            type="button"
            @click="navigate(item.path)"
          >
            <el-icon class="sidebar__item-icon"><component :is="resolveDynamicComponent(item.icon)" /></el-icon>
            <span v-if="!appStore.sidebarCollapsed">{{ item.title }}</span>
          </button>
        </div>
      </section>
    </div>

    <div class="sidebar__footer">
      <button class="sidebar__collapse-trigger" type="button" @click="appStore.toggleSidebar()">
        <el-icon class="sidebar__collapse-icon">
          <component :is="appStore.sidebarCollapsed ? ArrowRightBold : ArrowLeftBold" />
        </el-icon>
        <span v-if="!appStore.sidebarCollapsed">收起侧栏</span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  width: 248px;
  min-width: 248px;
  padding: 14px 12px;
  border-right: 1px solid #d8dee9;
  background: #eef2f6;
  color: #1f2937;
  transition: width 0.2s ease, min-width 0.2s ease;
}

.sidebar--collapsed {
  width: 84px;
  min-width: 84px;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 8px 18px;
}

.sidebar__logo {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #dbe7ff;
  color: #1d4ed8;
  font-weight: 700;
}

.sidebar__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sidebar__title span {
  color: #6b7280;
  font-size: 12px;
}

.sidebar__menus {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
}

.sidebar__group {
  overflow: hidden;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}

.sidebar__group-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 14px 16px;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.sidebar__group-trigger.is-active,
.sidebar__group-trigger:hover {
  background: rgba(255, 255, 255, 0.85);
}

.sidebar__group-trigger.is-open {
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.sidebar__group-label {
  font-size: 15px;
  font-weight: 500;
}

.sidebar__group-arrow {
  color: #4b5563;
}

.sidebar__group-icon {
  color: #64748b;
}

.sidebar__group-items {
  padding: 6px 10px 10px;
}

.sidebar__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-top: 6px;
  padding: 12px 14px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #374151;
  text-align: left;
  cursor: pointer;
}

.sidebar__item:hover {
  background: #f8fafc;
}

.sidebar__item.is-active {
  background: #e3ebf8;
  color: #1f2937;
  font-weight: 600;
}

.sidebar__item-icon {
  color: #64748b;
}

.sidebar__footer {
  padding-top: 12px;
}

.sidebar__collapse-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #d8dee9;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
  color: #334155;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.sidebar__collapse-trigger:hover {
  background: #ffffff;
  border-color: #c3cedb;
}

.sidebar__collapse-icon {
  color: #64748b;
}

.sidebar--collapsed .sidebar__group {
  background: transparent;
}

.sidebar--collapsed .sidebar__group-trigger {
  justify-content: center;
  padding: 12px 0;
  border-radius: 8px;
}

.sidebar--collapsed .sidebar__group-items {
  display: none;
}

.sidebar--collapsed .sidebar__footer {
  padding-top: 8px;
}

.sidebar--collapsed .sidebar__collapse-trigger {
  padding: 12px 0;
}

.sidebar--collapsed .sidebar__item {
  justify-content: center;
  margin-top: 0;
  padding: 12px 0;
}
</style>
