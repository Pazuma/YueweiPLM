export type ObjectType =
  | 'customer'
  | 'product'
  | 'order'
  | 'production-order'
  | 'process'
  | 'inventory'
  | 'workstation'

export type CommonStatus =
  | 'draft'
  | 'active'
  | 'inactive'
  | 'developing'
  | 'reviewing'
  | 'released'
  | 'archived'
  | 'confirmed'
  | 'in_production'
  | 'completed'
  | 'closed'
  | 'scheduled'
  | 'in_progress'
  | 'locked'
  | 'changed'
  | 'available'
  | 'reserved'
  | 'consumed'
  | 'in_use'
  | 'maintenance'
  | 'pending'
  | 'approved'
  | 'rejected'
  | 'blocked'

export interface UserProfile {
  userId: number
  userName: string
  roleName: string
  department: string
}

export interface SearchFieldOption {
  label: string
  value: string | number
}

export interface SearchField {
  prop: string
  label: string
  type: 'input' | 'select' | 'date'
  placeholder?: string
  options?: SearchFieldOption[]
}

export interface TimelineItem {
  title: string
  time: string | null
  owner?: string
  description?: string
  status?: CommonStatus
}

export interface ApprovalStep {
  stepName: string
  approver: string
  status: CommonStatus
  time: string | null
  comment: string
}

export interface OperateLogEntry {
  time: string
  operator: string
  action: string
  level: 'normal' | 'danger'
}

export interface MenuItem {
  path: string
  title: string
  icon: string
  permission: string
}

export interface MenuGroup {
  title: string
  items: MenuItem[]
}

export interface DashboardKpi {
  label: string
  value: number
  trend: string
  targetPath: string
}

export interface DashboardMilestone {
  stage: string
  owner: string
  dueDate: string
  status: CommonStatus
  summary: string
  targetPath: string
}

export interface DashboardRisk {
  title: string
  level: '高' | '中' | '低'
  owner: string
  action: string
  targetPath: string
}

export interface DashboardStageOverview {
  stage: string
  total: number
  delayed: number
  completionRate: number
  targetPath: string
}

export interface DashboardFocusItem {
  title: string
  owner: string
  dueDate: string
  status: CommonStatus
  summary: string
  targetPath: string
}

export interface DashboardTodoItem {
  title: string
  owner: string
  dueDate: string
  tag: string
  targetPath: string
}

export interface DashboardSnapshot {
  hero: {
    title: string
    subtitle: string
  }
  kpis: DashboardKpi[]
  milestones: DashboardMilestone[]
  risks: DashboardRisk[]
  stageOverview: DashboardStageOverview[]
  focusItems: DashboardFocusItem[]
  todoItems: DashboardTodoItem[]
}

export interface ApprovalTask {
  taskId: number
  objectType: ObjectType
  objectName: string
  nodeName: string
  initiator: string
  approver: string
  dueDate: string
  status: CommonStatus
  targetPath?: string
}

export interface UserOption {
  userId: number
  userName: string
  roleName: string
  department: string
}
