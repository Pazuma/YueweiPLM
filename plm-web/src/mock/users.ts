import type {
  ApprovalTask,
  ApprovalTemplate,
  ApprovalTemplateNode,
  SystemPermissionGroup,
  SystemRoleItem,
  SystemUserItem,
  UserOption,
  UserProfile
} from '@/types/common'

export const mockUsers: UserOption[] = [
  { userId: 1, userName: '张敏', roleName: '项目经理', department: '项目部' },
  { userId: 2, userName: '刘浩', roleName: '工程', department: '工程部' },
  { userId: 3, userName: '王总', roleName: '管理层', department: '管理层' },
  { userId: 4, userName: '赵越', roleName: '超级管理员', department: '信息部' },
  { userId: 5, userName: '李琴', roleName: '品质主管', department: '品质部' },
  { userId: 6, userName: '陈锋', roleName: '采购', department: '采购部' },
  { userId: 7, userName: '孙涛', roleName: '模具工程师', department: '工程部' }
]

export const mockAccounts: Array<{ username: string; password: string; profile: UserProfile }> = [
  { username: 'pm', password: '123456', profile: { userId: 1, userName: '张敏', roleName: '项目经理', department: '项目部' } },
  { username: 'engineer', password: '123456', profile: { userId: 2, userName: '刘浩', roleName: '工程', department: '工程部' } },
  { username: 'manager', password: '123456', profile: { userId: 3, userName: '王总', roleName: '管理层', department: '管理层' } },
  { username: 'admin', password: '123456', profile: { userId: 4, userName: '赵越', roleName: '超级管理员', department: '信息部' } }
]

export const rolePermissions: Record<string, string[]> = {
  超级管理员: [
    'dashboard:view',
    'product:view',
    'order:view',
    'inventory:view',
    'supplier:view',
    'project:view',
    'approval:view',
    'report:view',
    'process:view',
    'quality:view',
    'quality:create',
    'quality:edit',
    'admin:force-advance',
    'admin:user',
    'admin:role',
    'admin:field',
    'admin:log',
    'admin:import'
  ],
  项目经理: ['dashboard:view', 'product:view', 'order:view', 'project:view', 'approval:view', 'report:view'],
  工程: ['dashboard:view', 'product:view', 'process:view', 'project:view', 'approval:view'],
  管理层: ['dashboard:view', 'product:view', 'order:view', 'project:view', 'approval:view', 'report:view'],
  品质主管: ['dashboard:view', 'product:view', 'quality:view', 'quality:create', 'quality:edit', 'approval:view'],
  采购: ['dashboard:view', 'product:view', 'inventory:view', 'supplier:view', 'approval:view'],
  模具工程师: ['dashboard:view', 'product:view', 'process:view', 'project:view', 'approval:view']
}

export const approvalTasks: ApprovalTask[] = [
  {
    taskId: 1,
    objectType: 'product',
    objectName: '超队 3.0 / iPhone18 / 黑色',
    nodeName: '版本发布审批',
    initiator: '刘浩',
    approver: '王总',
    dueDate: '2026-06-12',
    status: 'pending',
    targetPath: '/products/102'
  },
  {
    taskId: 2,
    objectType: 'order',
    objectName: '亮甲 3.0 墨西哥客户样品需求',
    nodeName: '品质确认',
    initiator: '张敏',
    approver: '李琴',
    dueDate: '2026-06-07',
    status: 'approved',
    targetPath: '/orders'
  },
  {
    taskId: 3,
    objectType: 'product',
    objectName: '超队 3.0 新产品线',
    nodeName: '资料冻结确认',
    initiator: '张敏',
    approver: '李琴',
    dueDate: '2026-06-10',
    status: 'pending',
    targetPath: '/products/101'
  },
  {
    taskId: 4,
    objectType: 'process',
    objectName: '超队 3.0 改模工艺差异',
    nodeName: '模具分支确认',
    initiator: '孙涛',
    approver: '刘浩',
    dueDate: '2026-06-11',
    status: 'rejected',
    targetPath: '/processes'
  }
]

function createTemplateNode(
  nodeId: number,
  stepNo: number,
  nodeName: string,
  approverRole: string,
  approverUserName: string,
  isGate: boolean,
  note: string
): ApprovalTemplateNode {
  const user = mockUsers.find((item) => item.userName === approverUserName)
  return {
    nodeId,
    stepNo,
    nodeName,
    approverRole,
    approverUserId: user?.userId,
    approverUserName,
    isGate,
    note
  }
}

export const approvalTemplates: ApprovalTemplate[] = [
  {
    templateId: 1,
    templateName: '新产品线立项与开模审批',
    objectType: 'product',
    flowType: '新产品线',
    status: 'active',
    description: '覆盖新产品线从立项、开模到签样的关键门禁节点。',
    updatedAt: '2026-06-10',
    nodes: [
      createTemplateNode(101, 1, '立项审批', '管理层', '王总', true, '确认立项说明书、目标机型、成本边界和开发周期。'),
      createTemplateNode(102, 2, '开模前门禁', '工程', '刘浩', true, '检查结构方案、关键 BOM、工艺路径、颜色外观与包装测试要求。'),
      createTemplateNode(103, 3, '签样确认', '项目经理', '张敏', true, '样品版本冻结前的外观和结构最终确认。')
    ]
  },
  {
    templateId: 2,
    templateName: '新型号线差异发布审批',
    objectType: 'product',
    flowType: '新型号线',
    status: 'active',
    description: '用于型号扩展、颜色扩展和改模分支的差异审批。',
    updatedAt: '2026-06-09',
    nodes: [
      createTemplateNode(201, 1, '扩展确认', '项目经理', '张敏', true, '确认需求来源和父产品继承边界。'),
      createTemplateNode(202, 2, '模具分支确认', '模具工程师', '孙涛', true, '确认改模 / 新开模 / 跳过模具的处理路径。'),
      createTemplateNode(203, 3, '差异测试确认', '品质主管', '李琴', false, '只对变化部分进行验证并确认闭环。'),
      createTemplateNode(204, 4, '版本冻结', '管理层', '王总', true, '冻结 BOM、工艺、图纸和发布条件。')
    ]
  },
  {
    templateId: 3,
    templateName: '资料冻结审批',
    objectType: 'process',
    flowType: '版本冻结',
    status: 'draft',
    description: '用于产品详情页中的资料冻结动作前置审批。',
    updatedAt: '2026-06-08',
    nodes: [
      createTemplateNode(301, 1, '资料齐套确认', '工程', '刘浩', false, '确认图纸、SOP、SIP、测试记录齐全。'),
      createTemplateNode(302, 2, '品质复核', '品质主管', '李琴', true, '确认红样 / 黄样 / 小批验证结论完整。'),
      createTemplateNode(303, 3, '冻结批准', '管理层', '王总', true, '批准冻结并进入正式发布前状态。')
    ]
  }
]

export const approvalTemplateOptions = {
  objectTypes: [
    { label: '产品', value: 'product' },
    { label: '订单', value: 'order' },
    { label: '工艺', value: 'process' }
  ],
  flowTypes: ['新产品线', '新型号线', '开模申请', '版本冻结', '正式发布'],
  statuses: [
    { label: '草稿', value: 'draft' },
    { label: '启用', value: 'active' },
    { label: '停用', value: 'inactive' }
  ],
  roleOptions: Array.from(new Set(mockUsers.map((item) => item.roleName))).map((role) => ({
    label: role,
    value: role
  })),
  userOptions: mockUsers.map((item) => ({
    label: `${item.userName} / ${item.roleName}`,
    value: item.userId,
    roleName: item.roleName
  }))
}

export const systemPermissionGroups: SystemPermissionGroup[] = [
  {
    groupKey: 'dashboard',
    groupName: '工作台与报表',
    options: [
      { label: '工作台查看', value: 'dashboard:view' },
      { label: '报表中心查看', value: 'report:view' }
    ]
  },
  {
    groupKey: 'product',
    groupName: '产品研发',
    options: [
      { label: '产品查看', value: 'product:view' },
      { label: '产品新建', value: 'product:create' },
      { label: '产品编辑', value: 'product:edit' },
      { label: '产品发布', value: 'product:publish' },
      { label: '产品冻结', value: 'product:freeze' },
      { label: '项目查看', value: 'project:view' },
      { label: '项目编辑', value: 'project:edit' }
    ]
  },
  {
    groupKey: 'execution',
    groupName: '工艺与执行',
    options: [
      { label: '工艺查看', value: 'process:view' },
      { label: '工艺编辑', value: 'process:edit' },
      { label: '测试查看', value: 'quality:view' },
      { label: '测试编辑', value: 'quality:edit' },
      { label: '物料查看', value: 'inventory:view' },
      { label: '物料编辑', value: 'inventory:edit' },
      { label: '供应商查看', value: 'supplier:view' },
      { label: '供应商编辑', value: 'supplier:edit' }
    ]
  },
  {
    groupKey: 'approval',
    groupName: '审批与控制',
    options: [
      { label: '审批中心查看', value: 'approval:view' },
      { label: '强制推进', value: 'admin:force-advance' }
    ]
  },
  {
    groupKey: 'system',
    groupName: '系统管理',
    options: [
      { label: '用户管理', value: 'admin:user' },
      { label: '角色管理', value: 'admin:role' },
      { label: '字段管理', value: 'admin:field' },
      { label: '操作日志', value: 'admin:log' },
      { label: '数据导入', value: 'admin:import' }
    ]
  }
]

export const systemUsers: SystemUserItem[] = [
  {
    userId: 1,
    userName: '张敏',
    loginName: 'pm',
    departmentName: '项目部',
    roleNames: ['项目经理'],
    status: 'active',
    isSuperAdmin: false,
    lastLoginAt: '2026-06-10 09:12',
    currentProjectCount: 4,
    pendingApprovalCount: 2,
    phone: '13800010001',
    email: 'zhangmin@yuewei.com',
    note: '负责超队 3.0 与亮甲 3.0 项目推进。'
  },
  {
    userId: 2,
    userName: '刘浩',
    loginName: 'engineer',
    departmentName: '工程部',
    roleNames: ['工程'],
    status: 'active',
    isSuperAdmin: false,
    lastLoginAt: '2026-06-10 08:46',
    currentProjectCount: 6,
    pendingApprovalCount: 1,
    phone: '13800010002',
    email: 'liuhao@yuewei.com',
    note: '负责结构图纸、BOM 差异与工艺收口。'
  },
  {
    userId: 3,
    userName: '王涛',
    loginName: 'manager',
    departmentName: '管理层',
    roleNames: ['管理层'],
    status: 'active',
    isSuperAdmin: false,
    lastLoginAt: '2026-06-09 18:22',
    currentProjectCount: 0,
    pendingApprovalCount: 5,
    phone: '13800010003',
    email: 'wangtao@yuewei.com',
    note: '负责立项、冻结、发布等关键门禁审批。'
  },
  {
    userId: 4,
    userName: '赵越',
    loginName: 'admin',
    departmentName: '信息部',
    roleNames: ['超级管理员'],
    status: 'active',
    isSuperAdmin: true,
    lastLoginAt: '2026-06-10 10:05',
    currentProjectCount: 0,
    pendingApprovalCount: 0,
    phone: '13800010004',
    email: 'zhaoyue@yuewei.com',
    note: '具备系统配置、越权审批和强制推进权限。'
  },
  {
    userId: 5,
    userName: '李琴',
    loginName: 'quality',
    departmentName: '品质部',
    roleNames: ['品质主管'],
    status: 'active',
    isSuperAdmin: false,
    lastLoginAt: '2026-06-09 17:14',
    currentProjectCount: 3,
    pendingApprovalCount: 3,
    phone: '13800010005',
    email: 'liqin@yuewei.com',
    note: '负责红样、黄样、小批量与合规资料复核。'
  },
  {
    userId: 6,
    userName: '陈锐',
    loginName: 'buyer',
    departmentName: '采购部',
    roleNames: ['采购'],
    status: 'inactive',
    isSuperAdmin: false,
    lastLoginAt: '2026-06-04 11:20',
    currentProjectCount: 2,
    pendingApprovalCount: 0,
    phone: '13800010006',
    email: 'chenrui@yuewei.com',
    note: '当前处于停用演示状态。'
  }
]

export const systemRoles: SystemRoleItem[] = [
  {
    roleId: 1,
    roleName: '项目经理',
    roleCode: 'project_manager',
    status: 'active',
    memberCount: 1,
    description: '负责产品立项发起、进度推进、项目协调和阶段跟踪。',
    dataScopeLabel: '负责项目',
    permissions: ['dashboard:view', 'product:view', 'order:view', 'project:view', 'approval:view', 'report:view'],
    memberNames: ['张敏']
  },
  {
    roleId: 2,
    roleName: '工程',
    roleCode: 'engineer',
    status: 'active',
    memberCount: 1,
    description: '负责图纸、BOM、工艺路线、版本资料与差异确认。',
    dataScopeLabel: '工程相关数据',
    permissions: ['dashboard:view', 'product:view', 'process:view', 'project:view', 'approval:view', 'quality:view'],
    memberNames: ['刘浩']
  },
  {
    roleId: 3,
    roleName: '管理层',
    roleCode: 'management',
    status: 'active',
    memberCount: 1,
    description: '负责立项、开模、冻结、发布等关键审批和经营看板。',
    dataScopeLabel: '全公司汇总数据',
    permissions: ['dashboard:view', 'product:view', 'order:view', 'project:view', 'approval:view', 'report:view'],
    memberNames: ['王涛']
  },
  {
    roleId: 4,
    roleName: '超级管理员',
    roleCode: 'super_admin',
    status: 'active',
    memberCount: 1,
    description: '负责系统配置、越权审批、强制推进与紧急兜底控制。',
    dataScopeLabel: '全量数据',
    permissions: [
      'dashboard:view',
      'product:view',
      'product:create',
      'product:edit',
      'product:publish',
      'product:freeze',
      'project:view',
      'process:view',
      'quality:view',
      'approval:view',
      'report:view',
      'admin:force-advance',
      'admin:user',
      'admin:role',
      'admin:field',
      'admin:log',
      'admin:import'
    ],
    memberNames: ['赵越']
  },
  {
    roleId: 5,
    roleName: '品质主管',
    roleCode: 'quality_lead',
    status: 'active',
    memberCount: 1,
    description: '负责测试、合规资料、异常闭环与放行复核。',
    dataScopeLabel: '品质相关数据',
    permissions: ['dashboard:view', 'product:view', 'quality:view', 'quality:create', 'quality:edit', 'approval:view'],
    memberNames: ['李琴']
  },
  {
    roleId: 6,
    roleName: '采购',
    roleCode: 'purchasing',
    status: 'inactive',
    memberCount: 1,
    description: '负责供应商、物料、报价、交期和替代料确认。',
    dataScopeLabel: '采购与成本相关数据',
    permissions: ['dashboard:view', 'product:view', 'inventory:view', 'supplier:view', 'approval:view'],
    memberNames: ['陈锐']
  }
]
