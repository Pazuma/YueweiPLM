<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getSystemPermissionGroups, getSystemRoles } from '@/api/modules/system'
import PageContainer from '@/components/PageContainer/index.vue'
import type { SystemPermissionGroup, SystemRoleItem } from '@/types/common'

interface RoleFormState {
  roleId: number | null
  roleName: string
  roleCode: string
  status: 'active' | 'inactive'
  description: string
  dataScopeLabel: string
  permissions: string[]
}

const loading = ref(false)
const drawerVisible = ref(false)
const selectedRoleId = ref<number | null>(null)
const roles = ref<SystemRoleItem[]>([])
const permissionGroups = ref<SystemPermissionGroup[]>([])

const query = reactive({
  keyword: '',
  status: ''
})

const form = reactive<RoleFormState>({
  roleId: null,
  roleName: '',
  roleCode: '',
  status: 'active',
  description: '',
  dataScopeLabel: '',
  permissions: []
})

const summary = computed(() => ({
  total: roles.value.length,
  active: roles.value.filter((item) => item.status === 'active').length,
  forceAdvance: roles.value.filter((item) => item.permissions.includes('admin:force-advance')).length
}))

const filteredRoles = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  return roles.value.filter((item) => {
    const keywordMatched =
      !keyword ||
      item.roleName.toLowerCase().includes(keyword) ||
      item.roleCode.toLowerCase().includes(keyword) ||
      item.description.toLowerCase().includes(keyword)

    const statusMatched = !query.status || item.status === query.status
    return keywordMatched && statusMatched
  })
})

const selectedRole = computed(() => {
  return filteredRoles.value.find((item) => item.roleId === selectedRoleId.value) || filteredRoles.value[0] || null
})

const permissionCount = computed(() => form.permissions.length)

function resetForm(role?: SystemRoleItem) {
  form.roleId = role?.roleId ?? null
  form.roleName = role?.roleName ?? ''
  form.roleCode = role?.roleCode ?? ''
  form.status = role?.status ?? 'active'
  form.description = role?.description ?? ''
  form.dataScopeLabel = role?.dataScopeLabel ?? ''
  form.permissions = role ? [...role.permissions] : []
}

function openCreateDrawer() {
  resetForm()
  drawerVisible.value = true
}

function openEditDrawer(role: SystemRoleItem) {
  resetForm(role)
  drawerVisible.value = true
}

function saveRole() {
  if (!form.roleName.trim() || !form.roleCode.trim()) {
    ElMessage.warning('请填写角色名称和角色编码。')
    return
  }

  if (!form.permissions.length) {
    ElMessage.warning('请至少勾选一个权限。')
    return
  }

  const existing = form.roleId ? roles.value.find((item) => item.roleId === form.roleId) : null
  const payload: SystemRoleItem = {
    roleId: form.roleId ?? Date.now(),
    roleName: form.roleName.trim(),
    roleCode: form.roleCode.trim(),
    status: form.status,
    memberCount: existing?.memberCount ?? 0,
    description: form.description.trim(),
    dataScopeLabel: form.dataScopeLabel.trim() || '按角色授权范围',
    permissions: [...form.permissions],
    memberNames: existing?.memberNames ? [...existing.memberNames] : []
  }

  const index = roles.value.findIndex((item) => item.roleId === payload.roleId)
  if (index >= 0) {
    roles.value.splice(index, 1, payload)
    ElMessage.success('角色已更新')
  } else {
    roles.value.unshift(payload)
    ElMessage.success('角色已新增')
  }

  selectedRoleId.value = payload.roleId
  drawerVisible.value = false
}

async function loadData() {
  loading.value = true
  try {
    const [roleRows, permissionRows] = await Promise.all([getSystemRoles(), getSystemPermissionGroups()])
    roles.value = roleRows
    permissionGroups.value = permissionRows
    selectedRoleId.value = roleRows[0]?.roleId ?? null
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer
    title="角色管理"
    description="配置角色职责、菜单与按钮权限、数据范围，以及强制推进等高风险能力的归属边界。"
  >
    <template #actions>
      <el-button type="primary" @click="openCreateDrawer">新增角色</el-button>
    </template>

    <section class="page-panel filter-panel">
      <div class="filter-grid">
        <el-input v-model="query.keyword" clearable placeholder="搜索角色名称 / 编码 / 描述" />
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="启用中" value="active" />
          <el-option label="停用中" value="inactive" />
        </el-select>
      </div>
    </section>

    <section class="split-grid role-layout" v-loading="loading">
      <article class="page-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">角色列表</h3>
            <p class="page-panel-desc">先确定谁承担哪类业务职责，再给出对应页面和操作权限。</p>
          </div>
        </div>

        <div class="page-stack">
          <button
            v-for="role in filteredRoles"
            :key="role.roleId"
            class="role-card"
            :class="{ 'is-active': selectedRole?.roleId === role.roleId }"
            type="button"
            @click="selectedRoleId = role.roleId"
          >
            <div class="toolbar-row">
              <div class="cell-stack">
                <strong>{{ role.roleName }}</strong>
                <span class="subtle-text">{{ role.roleCode }}</span>
              </div>
              <el-tag :type="role.status === 'active' ? 'success' : 'info'" effect="light">
                {{ role.status === 'active' ? '启用中' : '停用中' }}
              </el-tag>
            </div>
            <p class="page-panel-desc">{{ role.description }}</p>
            <div class="detail-row">
              <span>{{ role.memberCount }} 人</span>
              <span>{{ role.permissions.length }} 项权限</span>
            </div>
          </button>
        </div>
      </article>

      <article class="page-panel" v-if="selectedRole">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">{{ selectedRole.roleName }}</h3>
            <p class="page-panel-desc">{{ selectedRole.description }}</p>
          </div>
          <div class="row-actions">
            <el-tag v-if="selectedRole.permissions.includes('admin:force-advance')" type="danger" effect="light">强制推进</el-tag>
            <el-button type="primary" plain @click="openEditDrawer(selectedRole)">编辑角色</el-button>
          </div>
        </div>

        <section class="role-meta-grid">
          <div class="info-card">
            <span class="subtle-text">角色编码</span>
            <strong>{{ selectedRole.roleCode }}</strong>
          </div>
          <div class="info-card">
            <span class="subtle-text">数据范围</span>
            <strong>{{ selectedRole.dataScopeLabel }}</strong>
          </div>
          <div class="info-card">
            <span class="subtle-text">成员数量</span>
            <strong>{{ selectedRole.memberCount }}</strong>
          </div>
          <div class="info-card">
            <span class="subtle-text">状态</span>
            <strong>{{ selectedRole.status === 'active' ? '启用中' : '停用中' }}</strong>
          </div>
        </section>

        <section class="page-panel role-subpanel">
          <h4 class="section-title">成员</h4>
          <div class="tag-list">
            <el-tag v-for="member in selectedRole.memberNames" :key="member" effect="light">{{ member }}</el-tag>
          </div>
        </section>

        <section class="page-panel role-subpanel">
          <h4 class="section-title">权限清单</h4>
          <div class="permission-group-list">
            <div v-for="group in permissionGroups" :key="group.groupKey" class="permission-group-card">
              <strong>{{ group.groupName }}</strong>
              <div class="tag-list">
                <el-tag
                  v-for="option in group.options.filter((option) => selectedRole.permissions.includes(option.value))"
                  :key="option.value"
                  effect="light"
                >
                  {{ option.label }}
                </el-tag>
                <span
                  v-if="!group.options.some((option) => selectedRole.permissions.includes(option.value))"
                  class="subtle-text"
                >
                  本组未分配权限
                </span>
              </div>
            </div>
          </div>
        </section>
      </article>
    </section>

    <el-drawer v-model="drawerVisible" :title="form.roleId ? '编辑角色' : '新增角色'" size="760px">
      <div class="page-stack">
        <section class="drawer-grid">
          <el-input v-model="form.roleName" placeholder="角色名称" />
          <el-input v-model="form.roleCode" placeholder="角色编码" />
          <el-select v-model="form.status" placeholder="状态">
            <el-option label="启用中" value="active" />
            <el-option label="停用中" value="inactive" />
          </el-select>
          <el-input v-model="form.dataScopeLabel" placeholder="数据范围说明" />
        </section>

        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="角色职责说明" />

        <section class="page-panel permission-editor">
          <div class="toolbar-row">
            <div>
              <h3 class="section-title">权限分配</h3>
              <p class="page-panel-desc">页面权限、按钮权限和高风险能力统一按组勾选。</p>
            </div>
            <el-tag effect="light">{{ permissionCount }} 项已选</el-tag>
          </div>

          <div class="permission-editor__groups">
            <div v-for="group in permissionGroups" :key="group.groupKey" class="permission-editor__group">
              <strong>{{ group.groupName }}</strong>
              <el-checkbox-group v-model="form.permissions" class="permission-editor__options">
                <el-checkbox v-for="option in group.options" :key="option.value" :label="option.value">
                  {{ option.label }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </section>

        <div class="drawer-actions">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" @click="saveRole">保存角色</el-button>
        </div>
      </div>
    </el-drawer>
  </PageContainer>
</template>

<style scoped>
.summary-button,
.role-card {
  width: 100%;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.summary-button:hover,
.role-card:hover,
.role-card.is-active {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.filter-panel,
.role-subpanel,
.permission-editor {
  box-shadow: none;
}

.filter-grid,
.drawer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.role-layout {
  grid-template-columns: minmax(320px, 0.9fr) minmax(0, 1.1fr);
}

.role-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}

.info-card,
.permission-group-card,
.permission-editor__group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.permission-group-list,
.permission-editor__groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.permission-editor__options,
.tag-list,
.row-actions,
.drawer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.drawer-actions {
  justify-content: flex-end;
  margin-top: 8px;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: var(--plm-color-text-secondary);
  font-size: var(--plm-font-size-sm);
}

@media (max-width: 1100px) {
  .role-layout,
  .filter-grid,
  .drawer-grid,
  .role-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
