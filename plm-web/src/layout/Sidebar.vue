<script setup lang="ts">
import { computed, reactive, ref, resolveDynamicComponent, watch } from 'vue'
import { ArrowDown, ArrowLeftBold, ArrowRightBold, ArrowUp } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'

import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import type { MenuGroup, MenuItem } from '@/types/common'

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
    title: '基础资料',
    items: [
      { path: '/files', title: '文件中心', icon: 'FolderOpened', permission: 'product:view' },
      { path: '/processes', title: '工艺管理', icon: 'Connection', permission: 'process:view' },
      { path: '/bom', title: 'BOM 管理', icon: 'List', permission: 'product:view' },
      { path: '/code-center', title: '编码中心', icon: 'CollectionTag', permission: 'product:view' },
      { path: '/production-orders', title: '测试管理', icon: 'Finished', permission: 'production-order:view' },
      { path: '/suppliers', title: '供应商管理', icon: 'Van', permission: 'supplier:view' },
      { path: '/inventories', title: '物料库存', icon: 'Box', permission: 'inventory:view' },
      { path: '/orders', title: '需求订单', icon: 'Document', permission: 'order:view' }
    ]
  },
  {
    title: '项目管理',
    items: [
      { path: '/projects?tab=in_progress', title: '进行中', icon: 'Management', permission: 'project:view' },
      {
        path: '/projects?tab=archived&archiveView=overview',
        title: '已归档',
        icon: 'Select',
        permission: 'project:view',
        children: [
          { path: '/projects?tab=archived&archiveView=overview', title: '已归档', icon: 'Collection', permission: 'project:view' },
          { path: '/projects?tab=archived&archiveView=product', title: '产品管理', icon: 'Grid', permission: 'project:view' },
          { path: '/projects?tab=archived&archiveView=sku', title: 'SKU 管理', icon: 'Tickets', permission: 'project:view' }
        ]
      },
      { path: '/projects?tab=abandoned', title: '已放弃', icon: 'RemoveFilled', permission: 'project:view' }
    ]
  },
  {
    title: '报表中心',
    items: [{ path: '/reports', title: '报表入口', icon: 'DataAnalysis', permission: 'report:view' }]
  },
  {
    title: '系统管理',
    items: [
      { path: '/approval-tasks', title: '审批中心', icon: 'Checked', permission: 'approval:view' },
      { path: '/system/users', title: '用户管理', icon: 'Avatar', permission: 'admin:user' },
      { path: '/system/roles', title: '角色管理', icon: 'Key', permission: 'admin:role' },
      { path: '/system/fields', title: '字段管理', icon: 'SetUp', permission: 'admin:field' },
      { path: '/system/import', title: '数据导入', icon: 'Upload', permission: 'admin:import' }
    ]
  }
]

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

function isActive(path: string) {
  return normalizePath(route.fullPath) === normalizePath(path) || normalizePath(route.path) === normalizePath(path)
}

function hasActiveChild(item: MenuItem) {
  if (!item.children?.length) return false
  return item.children.some((child) => isActive(child.path))
}

function groupHasActiveItem(group: MenuGroup) {
  return group.items.some((item) => isActive(item.path) || hasActiveChild(item))
}

const activeGroupTitle = ref('')
const subMenuState = reactive<Record<string, boolean>>({})

function syncActiveGroup() {
  const matched = visibleMenus.value.find((group) => groupHasActiveItem(group))
  activeGroupTitle.value = matched?.title || visibleMenus.value[0]?.title || ''

  matched?.items.forEach((item) => {
    if (hasActiveChild(item)) {
      subMenuState[item.path] = true
    }
  })
}

watch(
  () => [route.fullPath, visibleMenus.value.map((group) => group.title).join('|')],
  () => {
    syncActiveGroup()
  },
  { immediate: true }
)

function isGroupOpen(group: MenuGroup) {
  if (appStore.sidebarCollapsed) return false
  return activeGroupTitle.value === group.title
}

function isSubMenuOpen(path: string) {
  return subMenuState[path] ?? false
}

function toggleGroup(title: string) {
  if (appStore.sidebarCollapsed) {
    appStore.setSidebarCollapsed(false)
    activeGroupTitle.value = title
    return
  }

  activeGroupTitle.value = activeGroupTitle.value === title ? '' : title
}

function handleGroupClick(group: MenuGroup) {
  const nextOpen = activeGroupTitle.value !== group.title
  toggleGroup(group.title)

  if (nextOpen && group.items[0]?.path) {
    navigate(group.items[0].path, group.title)
  }
}

function navigate(path: string, groupTitle: string) {
  activeGroupTitle.value = groupTitle
  router.push(path)
}

function handleMenuItemClick(item: MenuItem, groupTitle: string) {
  if (item.children?.length) {
    subMenuState[item.path] = true
  }

  navigate(item.path, groupTitle)
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
      <section v-for="group in visibleMenus" :key="group.title" class="sidebar__group" :class="{ 'is-active-group': groupHasActiveItem(group) }">
        <button
          class="sidebar__group-trigger"
          :class="{ 'is-open': isGroupOpen(group), 'is-active': groupHasActiveItem(group) }"
          type="button"
          @click="handleGroupClick(group)"
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
          <template v-for="item in group.items" :key="item.path">
            <button
              class="sidebar__item"
              :class="{ 'is-active': isActive(item.path) || hasActiveChild(item) }"
              type="button"
              @click="handleMenuItemClick(item, group.title)"
            >
              <el-icon class="sidebar__item-icon"><component :is="resolveDynamicComponent(item.icon)" /></el-icon>
              <span v-if="!appStore.sidebarCollapsed">{{ item.title }}</span>
              <el-icon v-if="item.children?.length && !appStore.sidebarCollapsed" class="sidebar__item-arrow">
                <component :is="isSubMenuOpen(item.path) ? ArrowUp : ArrowDown" />
              </el-icon>
            </button>

            <div v-if="item.children?.length && isSubMenuOpen(item.path) && !appStore.sidebarCollapsed" class="sidebar__sub-items">
              <button
                v-for="child in item.children"
                :key="child.path"
                class="sidebar__sub-item"
                :class="{ 'is-active': isActive(child.path) }"
                type="button"
                @click="navigate(child.path, group.title)"
              >
                <span>{{ child.title }}</span>
              </button>
            </div>
          </template>
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
  height: 100%;
  padding: 14px 10px 12px;
  border-right: 1px solid rgba(148, 163, 184, 0.16);
  background: #f3f5f7;
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
  padding: 8px 10px 18px;
}

.sidebar__logo {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.14), rgba(14, 165, 233, 0.12));
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
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.sidebar__group {
  overflow: visible;
  border-radius: 0;
  background: transparent;
  transition: background 0.2s ease;
}

.sidebar__group.is-active-group {
  background: transparent;
}

.sidebar__group-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 11px 12px;
  border: 0;
  background: transparent;
  border-radius: 10px;
  color: #475569;
  cursor: pointer;
  text-align: left;
  transition: background 0.2s ease, color 0.2s ease;
}

.sidebar__group-trigger.is-active,
.sidebar__group-trigger:hover {
  background: rgba(148, 163, 184, 0.1);
  color: #1f2937;
}

.sidebar__group-trigger.is-open {
  background: rgba(148, 163, 184, 0.12);
}

.sidebar__group-label {
  font-size: 14px;
  font-weight: 600;
}

.sidebar__group-arrow,
.sidebar__group-icon,
.sidebar__item-icon,
.sidebar__collapse-icon {
  color: #64748b;
}

.sidebar__group-items {
  margin-top: 4px;
  padding: 2px 0 6px;
}

.sidebar__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-top: 4px;
  padding: 11px 14px 11px 16px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #475569;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.sidebar__item:hover {
  background: rgba(148, 163, 184, 0.1);
  color: #1f2937;
}

.sidebar__item.is-active {
  position: relative;
  background: rgba(37, 99, 235, 0.08);
  color: #1d4ed8;
  font-weight: 600;
}

.sidebar__item.is-active::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 999px;
  background: #2563eb;
}

.sidebar__item-arrow {
  color: #94a3b8;
  margin-left: auto;
}

.sidebar__sub-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 4px 0 6px 28px;
  padding-left: 10px;
  border-left: 1px solid rgba(148, 163, 184, 0.22);
}

.sidebar__sub-item {
  width: 100%;
  padding: 9px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  text-align: left;
  cursor: pointer;
}

.sidebar__sub-item:hover,
.sidebar__sub-item.is-active {
  background: rgba(37, 99, 235, 0.08);
  color: #1d4ed8;
  font-weight: 600;
}

.sidebar__footer {
  padding-top: 10px;
}

.sidebar__collapse-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  padding: 11px 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.56);
  color: #334155;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.sidebar__collapse-trigger:hover {
  background: rgba(255, 255, 255, 0.88);
  border-color: rgba(148, 163, 184, 0.3);
}

.sidebar--collapsed .sidebar__group {
  background: transparent;
}

.sidebar--collapsed .sidebar__group-trigger {
  justify-content: center;
  padding: 12px 0;
  border-radius: 10px;
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
