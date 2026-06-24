import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

import Layout from '@/layout/index.vue'
import { useUserStore } from '@/stores/user'
import { normalizeLegacyProductTarget, toArchivedProductRoute, toArchivedSkuRoute, toInProgressProjectRoute } from '@/utils/projectRoute'

type RouteMetaConfig = {
  title: string
  permission?: string
  moduleKey?: string
  breadcrumb?: string[]
  subtitle?: string
}

function redirectLegacyProductList(to: { fullPath: string }) {
  return normalizeLegacyProductTarget(to.fullPath)
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: {
      title: '登录',
      breadcrumb: ['登录']
    } satisfies RouteMetaConfig
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
        meta: {
          title: '首页总览',
          permission: 'dashboard:view',
          breadcrumb: ['工作台', '首页总览'],
          subtitle: '聚焦当前推进中的产品、待办、风险和高频入口'
        } satisfies RouteMetaConfig
      },
      {
        path: 'products/create',
        name: 'ProductCreate',
        redirect: () => toInProgressProjectRoute(),
        meta: {
          title: '新项目',
          permission: 'project:view',
          breadcrumb: ['项目管理', '进行中']
        } satisfies RouteMetaConfig
      },
      {
        path: 'products/:id/edit',
        name: 'ProductEdit',
        redirect: (to) => toArchivedProductRoute(String(to.params.id)),
        meta: {
          title: '归档产品',
          permission: 'project:view',
          breadcrumb: ['项目管理', '已归档', '产品管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'products/:id',
        name: 'ProductDetail',
        redirect: (to) => toArchivedProductRoute(String(to.params.id)),
        meta: {
          title: '归档产品',
          permission: 'project:view',
          breadcrumb: ['项目管理', '已归档', '产品管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'products',
        name: 'ProductList',
        redirect: redirectLegacyProductList,
        meta: {
          title: '归档产品',
          permission: 'project:view',
          breadcrumb: ['项目管理', '已归档', '产品管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'sku-view',
        name: 'SkuView',
        redirect: () => toArchivedSkuRoute(),
        meta: {
          title: '归档 SKU',
          permission: 'project:view',
          breadcrumb: ['项目管理', '已归档', 'SKU 管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'bom',
        name: 'BomCenter',
        component: () => import('@/views/bom/BomCenterView.vue'),
        meta: {
          title: 'BOM 管理',
          permission: 'product:view',
          breadcrumb: ['基础资料', 'BOM 管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'customers',
        name: 'CustomerList',
        component: () => import('@/views/customer/CustomerList.vue'),
        meta: {
          title: '客户管理',
          permission: 'customer:view',
          breadcrumb: ['客户管理', '客户列表']
        } satisfies RouteMetaConfig
      },
      {
        path: 'customers/create',
        name: 'CustomerCreate',
        component: () => import('@/views/customer/CustomerEdit.vue'),
        meta: {
          title: '新建客户',
          permission: 'customer:create',
          breadcrumb: ['客户管理', '新建客户']
        } satisfies RouteMetaConfig
      },
      {
        path: 'customers/:id',
        name: 'CustomerDetail',
        component: () => import('@/views/customer/CustomerDetail.vue'),
        meta: {
          title: '客户详情',
          permission: 'customer:view',
          breadcrumb: ['客户管理', '客户详情']
        } satisfies RouteMetaConfig
      },
      {
        path: 'customers/:id/edit',
        name: 'CustomerEdit',
        component: () => import('@/views/customer/CustomerEdit.vue'),
        meta: {
          title: '编辑客户',
          permission: 'customer:edit',
          breadcrumb: ['客户管理', '编辑客户']
        } satisfies RouteMetaConfig
      },
      {
        path: 'orders',
        name: 'OrderList',
        component: () => import('@/views/order/OrderCenterView.vue'),
        meta: {
          title: '需求订单',
          permission: 'order:view',
          breadcrumb: ['基础资料', '需求订单']
        } satisfies RouteMetaConfig
      },
      {
        path: 'projects',
        name: 'ProjectCenter',
        component: () => import('@/views/project/ProjectCenterView.vue'),
        meta: {
          title: '项目管理',
          permission: 'project:view',
          breadcrumb: ['项目管理', '项目中心']
        } satisfies RouteMetaConfig
      },
      {
        path: 'production-orders',
        name: 'TestCenter',
        component: () => import('@/views/test/TestCenterView.vue'),
        meta: {
          title: '测试管理',
          permission: 'production-order:view',
          breadcrumb: ['基础资料', '测试管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'processes',
        name: 'ProcessList',
        component: () => import('@/views/process/ProcessCenterView.vue'),
        meta: {
          title: '工艺路线',
          permission: 'process:view',
          breadcrumb: ['基础资料', '工艺路线']
        } satisfies RouteMetaConfig
      },
      {
        path: 'inventories',
        name: 'InventoryCenter',
        component: () => import('@/views/inventory/InventoryCenterView.vue'),
        meta: {
          title: '物料库存',
          permission: 'inventory:view',
          breadcrumb: ['基础资料', '物料库存']
        } satisfies RouteMetaConfig
      },
      {
        path: 'files',
        name: 'FileCenter',
        component: () => import('@/views/file/FileCenterView.vue'),
        meta: {
          title: '文件中心',
          permission: 'product:view',
          breadcrumb: ['基础资料', '文件中心']
        } satisfies RouteMetaConfig
      },
      {
        path: 'suppliers',
        name: 'SupplierCenter',
        component: () => import('@/views/supplier/SupplierCenterView.vue'),
        meta: {
          title: '供应商管理',
          permission: 'supplier:view',
          breadcrumb: ['基础资料', '供应商管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'workstations',
        name: 'WorkstationList',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: {
          title: '机台工位',
          permission: 'workstation:view',
          moduleKey: 'workstation',
          breadcrumb: ['基础资料', '机台工位']
        } satisfies RouteMetaConfig
      },
      {
        path: 'quality',
        name: 'QualityList',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: {
          title: '质量管理',
          permission: 'quality:view',
          moduleKey: 'quality',
          breadcrumb: ['基础资料', '质量管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'costs',
        name: 'CostCenter',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: {
          title: '成本报价',
          permission: 'cost:view',
          moduleKey: 'cost',
          breadcrumb: ['基础资料', '成本报价']
        } satisfies RouteMetaConfig
      },
      {
        path: 'reports',
        name: 'ReportCenter',
        component: () => import('@/views/report/ReportCenterView.vue'),
        meta: {
          title: '报表入口',
          permission: 'report:view',
          breadcrumb: ['报表中心', '报表入口']
        } satisfies RouteMetaConfig
      },
      {
        path: 'approval-tasks',
        name: 'ApprovalTaskList',
        component: () => import('@/views/approval/ApprovalTaskList.vue'),
        meta: {
          title: '审批中心',
          permission: 'approval:view',
          breadcrumb: ['系统管理', '审批中心']
        } satisfies RouteMetaConfig
      },
      {
        path: 'system/users',
        name: 'SystemUsers',
        component: () => import('@/views/system/SystemUserManagementView.vue'),
        meta: {
          title: '用户管理',
          permission: 'admin:user',
          moduleKey: 'system-user',
          breadcrumb: ['系统管理', '用户管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'system/roles',
        name: 'SystemRoles',
        component: () => import('@/views/system/SystemRoleManagementView.vue'),
        meta: {
          title: '角色管理',
          permission: 'admin:role',
          moduleKey: 'system-role',
          breadcrumb: ['系统管理', '角色管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'system/fields',
        name: 'SystemFields',
        component: () => import('@/views/system/SystemFieldManagementView.vue'),
        meta: {
          title: '字段管理',
          permission: 'admin:field',
          moduleKey: 'system-field',
          breadcrumb: ['系统管理', '字段管理']
        } satisfies RouteMetaConfig
      },
      {
        path: 'system/dicts',
        redirect: '/system/fields'
      },
      {
        path: 'system/operation-log',
        name: 'SystemOperationLog',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: {
          title: '操作日志',
          permission: 'admin:log',
          moduleKey: 'system-log',
          breadcrumb: ['系统管理', '操作日志']
        } satisfies RouteMetaConfig
      },
      {
        path: 'system/import',
        name: 'SystemImport',
        component: () => import('@/views/module/ModulePlaceholderView.vue'),
        meta: {
          title: '数据导入',
          permission: 'admin:import',
          moduleKey: 'system-import',
          breadcrumb: ['系统管理', '数据导入']
        } satisfies RouteMetaConfig
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: {
      title: '无权限',
      breadcrumb: ['异常页面', '无权限']
    } satisfies RouteMetaConfig
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: {
      title: '页面不存在',
      breadcrumb: ['异常页面', '页面不存在']
    } satisfies RouteMetaConfig
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
