import { createRouter, createWebHashHistory } from 'vue-router'

import Layout from '@/layout/index.vue'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '工作台', permission: 'dashboard:view' }
      },
      {
        path: 'products',
        name: 'ProductList',
        component: () => import('@/views/product/ProductList.vue'),
        meta: { title: '产品管理', permission: 'product:view' }
      },
      {
        path: 'sku-view',
        name: 'SkuView',
        component: () => import('@/views/product/SkuView.vue'),
        meta: { title: 'SKU 视图', permission: 'product:view' }
      },
      {
        path: 'bom',
        name: 'BomCenter',
        component: () => import('@/views/bom/BomCenterView.vue'),
        meta: { title: 'BOM 管理', permission: 'product:view' }
      },
      {
        path: 'products/create',
        name: 'ProductCreate',
        component: () => import('@/views/product/ProductEdit.vue'),
        meta: { title: '新建产品', permission: 'product:create' }
      },
      {
        path: 'products/:id',
        name: 'ProductDetail',
        component: () => import('@/views/product/ProductDetail.vue'),
        meta: { title: '产品详情', permission: 'product:view' }
      },
      {
        path: 'products/:id/edit',
        name: 'ProductEdit',
        component: () => import('@/views/product/ProductEdit.vue'),
        meta: { title: '编辑产品', permission: 'product:edit' }
      },
      {
        path: 'customers',
        name: 'CustomerList',
        component: () => import('@/views/customer/CustomerList.vue'),
        meta: { title: '客户管理', permission: 'customer:view' }
      },
      {
        path: 'customers/create',
        name: 'CustomerCreate',
        component: () => import('@/views/customer/CustomerEdit.vue'),
        meta: { title: '新建客户', permission: 'customer:create' }
      },
      {
        path: 'customers/:id',
        name: 'CustomerDetail',
        component: () => import('@/views/customer/CustomerDetail.vue'),
        meta: { title: '客户详情', permission: 'customer:view' }
      },
      {
        path: 'customers/:id/edit',
        name: 'CustomerEdit',
        component: () => import('@/views/customer/CustomerEdit.vue'),
        meta: { title: '编辑客户', permission: 'customer:edit' }
      },
      {
        path: 'orders',
        name: 'OrderList',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: { title: '需求订单', permission: 'order:view', moduleKey: 'order' }
      },
      {
        path: 'projects',
        name: 'ProjectCenter',
        component: () => import('@/views/project/ProjectCenterView.vue'),
        meta: { title: '项目管理', permission: 'project:view' }
      },
      {
        path: 'production-orders',
        name: 'TestCenter',
        component: () => import('@/views/test/TestCenterView.vue'),
        meta: { title: '测试管理', permission: 'production-order:view' }
      },
      {
        path: 'processes',
        name: 'ProcessList',
        component: () => import('@/views/process/ProcessCenterView.vue'),
        meta: { title: '工艺路线', permission: 'process:view' }
      },
      {
        path: 'inventories',
        name: 'InventoryCenter',
        component: () => import('@/views/inventory/InventoryCenterView.vue'),
        meta: { title: '物料库存', permission: 'inventory:view' }
      },
      {
        path: 'files',
        name: 'FileCenter',
        component: () => import('@/views/file/FileCenterView.vue'),
        meta: { title: '文件管理', permission: 'product:view' }
      },
      {
        path: 'suppliers',
        name: 'SupplierCenter',
        component: () => import('@/views/supplier/SupplierCenterView.vue'),
        meta: { title: '供应商管理', permission: 'supplier:view' }
      },
      {
        path: 'workstations',
        name: 'WorkstationList',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: { title: '机台工位', permission: 'workstation:view', moduleKey: 'workstation' }
      },
      {
        path: 'quality',
        name: 'QualityList',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: { title: '质量管理', permission: 'quality:view', moduleKey: 'quality' }
      },
      {
        path: 'costs',
        name: 'CostCenter',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: { title: '成本报价', permission: 'cost:view', moduleKey: 'cost' }
      },
      {
        path: 'reports',
        name: 'ReportCenter',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: { title: '报表中心', permission: 'report:view', moduleKey: 'report' }
      },
      {
        path: 'approval-tasks',
        name: 'ApprovalTaskList',
        component: () => import('@/views/approval/ApprovalTaskList.vue'),
        meta: { title: '审批中心', permission: 'approval:view' }
      },
      {
        path: 'system/users',
        name: 'SystemUsers',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: { title: '用户管理', permission: 'admin:user', moduleKey: 'system-user' }
      },
      {
        path: 'system/roles',
        name: 'SystemRoles',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: { title: '角色管理', permission: 'admin:role', moduleKey: 'system-role' }
      },
      {
        path: 'system/dicts',
        name: 'SystemDicts',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: { title: '字典管理', permission: 'admin:dict', moduleKey: 'system-dict' }
      },
      {
        path: 'system/operation-log',
        name: 'SystemOperationLog',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: { title: '操作日志', permission: 'admin:log', moduleKey: 'system-log' }
      },
      {
        path: 'system/import',
        name: 'SystemImport',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: { title: '数据导入', permission: 'admin:import', moduleKey: 'system-import' }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '无权限' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

let restored = false

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (!restored && userStore.token) {
    restored = true
    await userStore.restore()
  }

  if (to.path !== '/login' && !userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.path === '/login' && userStore.isLoggedIn) {
    return { path: '/dashboard' }
  }

  const permission = to.meta.permission as string | undefined
  if (permission && !userStore.hasPermission(permission)) {
    return '/403'
  }

  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || 'Yuewei PLM'} - Yuewei PLM`
})

export default router
