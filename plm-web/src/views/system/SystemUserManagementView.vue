<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getSystemRoles, getSystemUsers } from '@/api/modules/system'
import FixedTableViewport from '@/components/FixedTableViewport/index.vue'
import PageContainer from '@/components/PageContainer/index.vue'
import type { SystemRoleItem, SystemUserItem } from '@/types/common'

interface UserFormState {
  userId: number | null
  userName: string
  loginName: string
  departmentName: string
  roleNames: string[]
  status: 'active' | 'inactive'
  isSuperAdmin: boolean
  phone: string
  email: string
  note: string
}

const loading = ref(false)
const drawerVisible = ref(false)
const users = ref<SystemUserItem[]>([])
const roles = ref<SystemRoleItem[]>([])

const query = reactive({
  keyword: '',
  status: '',
  roleName: '',
  departmentName: ''
})

const form = reactive<UserFormState>({
  userId: null,
  userName: '',
  loginName: '',
  departmentName: '',
  roleNames: [],
  status: 'active',
  isSuperAdmin: false,
  phone: '',
  email: '',
  note: ''
})

const summary = computed(() => ({
  total: users.value.length,
  active: users.value.filter((item) => item.status === 'active').length,
  inactive: users.value.filter((item) => item.status === 'inactive').length,
  superAdmins: users.value.filter((item) => item.isSuperAdmin).length
}))

const roleOptions = computed(() => roles.value.map((item) => item.roleName))

const departmentOptions = computed(() =>
  Array.from(new Set(users.value.map((item) => item.departmentName))).filter(Boolean)
)

const filteredUsers = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  return users.value.filter((item) => {
    const keywordMatched =
      !keyword ||
      item.userName.toLowerCase().includes(keyword) ||
      item.loginName.toLowerCase().includes(keyword) ||
      item.departmentName.toLowerCase().includes(keyword) ||
      item.roleNames.join(' / ').toLowerCase().includes(keyword)

    const statusMatched = !query.status || item.status === query.status
    const roleMatched = !query.roleName || item.roleNames.includes(query.roleName)
    const departmentMatched = !query.departmentName || item.departmentName === query.departmentName

    return keywordMatched && statusMatched && roleMatched && departmentMatched
  })
})

const selectedRolePermissions = computed(() => {
  const permissionSet = new Set<string>()
  roles.value
    .filter((role) => form.roleNames.includes(role.roleName))
    .forEach((role) => {
      role.permissions.forEach((permission) => permissionSet.add(permission))
    })

  return Array.from(permissionSet)
})

function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.roleName = ''
  query.departmentName = ''
}

function fillForm(user?: SystemUserItem) {
  form.userId = user?.userId ?? null
  form.userName = user?.userName ?? ''
  form.loginName = user?.loginName ?? ''
  form.departmentName = user?.departmentName ?? ''
  form.roleNames = user ? [...user.roleNames] : []
  form.status = user?.status ?? 'active'
  form.isSuperAdmin = user?.isSuperAdmin ?? false
  form.phone = user?.phone ?? ''
  form.email = user?.email ?? ''
  form.note = user?.note ?? ''
}

function openCreateDrawer() {
  fillForm()
  drawerVisible.value = true
}

function openEditDrawer(user: SystemUserItem) {
  fillForm(user)
  drawerVisible.value = true
}

function toggleStatus(user: SystemUserItem) {
  user.status = user.status === 'active' ? 'inactive' : 'active'
  ElMessage.success(user.status === 'active' ? '用户已启用' : '用户已停用')
}

function saveUser() {
  if (!form.userName.trim() || !form.loginName.trim() || !form.departmentName.trim()) {
    ElMessage.warning('请补齐用户姓名、登录账号和所属部门。')
    return
  }

  if (!form.roleNames.length) {
    ElMessage.warning('请至少绑定一个角色。')
    return
  }

  const payload: SystemUserItem = {
    userId: form.userId ?? Date.now(),
    userName: form.userName.trim(),
    loginName: form.loginName.trim(),
    departmentName: form.departmentName.trim(),
    roleNames: [...form.roleNames],
    status: form.status,
    isSuperAdmin: form.isSuperAdmin || form.roleNames.includes('超级管理员'),
    lastLoginAt: form.userId
      ? users.value.find((item) => item.userId === form.userId)?.lastLoginAt || '2026-06-10 10:00'
      : '未登录',
    currentProjectCount: form.userId
      ? users.value.find((item) => item.userId === form.userId)?.currentProjectCount || 0
      : 0,
    pendingApprovalCount: form.userId
      ? users.value.find((item) => item.userId === form.userId)?.pendingApprovalCount || 0
      : 0,
    phone: form.phone.trim(),
    email: form.email.trim(),
    note: form.note.trim()
  }

  const index = users.value.findIndex((item) => item.userId === payload.userId)
  if (index >= 0) {
    users.value.splice(index, 1, payload)
    ElMessage.success('用户信息已更新')
  } else {
    users.value.unshift(payload)
    ElMessage.success('用户已新增')
  }

  drawerVisible.value = false
}

async function loadData() {
  loading.value = true
  try {
    const [userRows, roleRows] = await Promise.all([getSystemUsers(), getSystemRoles()])
    users.value = userRows
    roles.value = roleRows
  } catch {
    users.value = []
    roles.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer
    title="用户管理"
    description="统一维护内部用户、所属部门、角色绑定、启停状态，以及当前参与项目和待审批任务概览。"
  >
    <template #actions>
      <el-button type="primary" @click="openCreateDrawer">新增用户</el-button>
    </template>

    <section class="page-panel filter-panel">
      <div class="filter-grid">
        <el-input v-model="query.keyword" clearable placeholder="搜索姓名 / 账号 / 部门 / 角色" />
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="启用中" value="active" />
          <el-option label="停用中" value="inactive" />
        </el-select>
        <el-select v-model="query.roleName" clearable placeholder="角色">
          <el-option v-for="role in roleOptions" :key="role" :label="role" :value="role" />
        </el-select>
        <el-select v-model="query.departmentName" clearable placeholder="部门">
          <el-option v-for="department in departmentOptions" :key="department" :label="department" :value="department" />
        </el-select>
      </div>
    </section>

    <section class="page-panel" v-loading="loading">
      <FixedTableViewport v-slot="{ tableHeight }" compact :refresh-key="filteredUsers">
      <el-table :data="filteredUsers" :height="tableHeight" border stripe>
        <el-table-column prop="userName" label="姓名" min-width="120" />
        <el-table-column prop="loginName" label="账号" min-width="120" />
        <el-table-column prop="departmentName" label="部门" min-width="120" />
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag v-for="role in row.roleNames" :key="role" effect="light">{{ role }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" effect="light">
              {{ row.status === 'active' ? '启用中' : '停用中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentProjectCount" label="参与项目" width="100" />
        <el-table-column prop="pendingApprovalCount" label="待审批" width="100" />
        <el-table-column prop="lastLoginAt" label="最近登录" min-width="150" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="openEditDrawer(row)">编辑</el-button>
              <el-button link :type="row.status === 'active' ? 'danger' : 'success'" @click="toggleStatus(row)">
                {{ row.status === 'active' ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      </FixedTableViewport>
    </section>

    <el-drawer v-model="drawerVisible" :title="form.userId ? '编辑用户' : '新增用户'" size="620px">
      <div class="page-stack">
        <section class="drawer-grid">
          <el-input v-model="form.userName" placeholder="用户姓名" />
          <el-input v-model="form.loginName" placeholder="登录账号" />
          <el-input v-model="form.departmentName" placeholder="所属部门" />
          <el-select v-model="form.status" placeholder="状态">
            <el-option label="启用中" value="active" />
            <el-option label="停用中" value="inactive" />
          </el-select>
        </section>

        <el-select v-model="form.roleNames" multiple collapse-tags collapse-tags-tooltip placeholder="绑定角色">
          <el-option v-for="role in roleOptions" :key="role" :label="role" :value="role" />
        </el-select>

        <section class="drawer-grid">
          <el-input v-model="form.phone" placeholder="联系电话" />
          <el-input v-model="form.email" placeholder="邮箱" />
        </section>

        <el-input v-model="form.note" type="textarea" :rows="3" placeholder="备注说明" />

        <section class="page-panel permission-preview">
          <div class="toolbar-row">
            <div>
              <h3 class="section-title">权限摘要</h3>
              <p class="page-panel-desc">当前角色绑定后，页面会按角色权限集合展示。</p>
            </div>
            <el-tag v-if="form.roleNames.includes('超级管理员')" type="danger" effect="light">超级管理员</el-tag>
          </div>
          <div class="tag-list">
            <el-tag v-for="permission in selectedRolePermissions" :key="permission" effect="light">{{ permission }}</el-tag>
          </div>
        </section>

        <div class="drawer-actions">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" @click="saveUser">保存用户</el-button>
        </div>
      </div>
    </el-drawer>
  </PageContainer>
</template>

<style scoped>
.summary-button {
  width: 100%;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.summary-button:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.filter-panel {
  box-shadow: none;
}

.filter-grid,
.drawer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

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

.permission-preview {
  box-shadow: none;
}

@media (max-width: 900px) {
  .filter-grid,
  .drawer-grid {
    grid-template-columns: 1fr;
  }
}
</style>
