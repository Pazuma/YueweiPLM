import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const bomApi = vi.hoisted(() => ({
  getProjectBoms: vi.fn(),
  getBomDetail: vi.fn(),
  createProjectBom: vi.fn(),
  updateBom: vi.fn(),
  addBomItem: vi.fn(),
  updateBomItem: vi.fn(),
  deleteBomItem: vi.fn(),
  freezeBom: vi.fn()
}))

const processApi = vi.hoisted(() => ({
  getProjectProcessRoutes: vi.fn(),
  getProcessRouteDetail: vi.fn(),
  createProcessRoute: vi.fn(),
  updateProcessRoute: vi.fn(),
  freezeProcessRoute: vi.fn()
}))

const attachmentApi = vi.hoisted(() => ({
  getTimelineAttachments: vi.fn(),
  uploadTimelineAttachment: vi.fn(),
  downloadAttachment: vi.fn(),
  deleteAttachment: vi.fn()
}))

const foundationApi = vi.hoisted(() => ({
  getProductPresentation: vi.fn()
}))

const projectApi = vi.hoisted(() => ({
  getProjects: vi.fn(),
  getProjectTimeline: vi.fn(),
  confirmTimelineNode: vi.fn(),
  advanceTimelineNode: vi.fn(),
  returnTimelineNode: vi.fn()
}))

const routeState = vi.hoisted(() => ({
  query: {} as Record<string, unknown>
}))

const routerPush = vi.hoisted(() => vi.fn())

vi.mock('@/api/modules/bom', () => bomApi)
vi.mock('@/api/modules/process', () => processApi)
vi.mock('@/api/modules/attachment', () => attachmentApi)
vi.mock('@/api/modules/foundation', () => foundationApi)
vi.mock('@/api/modules/project', () => projectApi)
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: routerPush })
}))

import ProjectCenterView from '../ProjectCenterView.vue'
import ProjectBomPanel from '../components/ProjectBomPanel.vue'
import ProjectProcessRoutePanel from '../components/ProjectProcessRoutePanel.vue'
import TimelineAttachmentPanel from '../components/TimelineAttachmentPanel.vue'

const mountOptions = {
  global: {
    plugins: [ElementPlus]
  }
}

function createProductSummary(overrides: Record<string, unknown> = {}) {
  return {
    productId: 9,
    productCode: 'PRD-9',
    productName: '测试产品',
    productType: 'product_line',
    seriesName: 'S',
    model: 'M',
    color: '黑色',
    material: 'TPU',
    ownerUserName: '工程',
    versionNo: 'A',
    status: 'archived',
    currentStage: '设计确认',
    currentStepNo: 2,
    customerName: '内部立项',
    frozenFlag: false,
    releasedAt: '2026-07-01',
    completionRate: 1,
    estimatedCost: 0,
    estimatedCostCurrency: 'CNY',
    activeBomVersion: 'A',
    testItemCount: 0,
    ...overrides
  }
}

function createPresentation(productId = 9, nodeKey = 'PRODUCT_LINE_DESIGN_CONFIRM') {
  return {
    productId,
    title: '测试产品',
    flowLabel: '新产品线',
    currentNode: '设计确认',
    nextNode: '推进下一节点',
    summary: '测试摘要',
    costPanel: { showEstimated: false, actualTotal: 0, actualLines: [] },
    timeline: [{
      nodeKey,
      nodeName: '设计确认',
      status: 'current',
      ownerRole: '工程',
      summary: '当前节点'
    }],
    bomCompareRows: [],
    bomItems: [],
    bomItemsByVersion: {},
    toolingSummary: { totalCount: 0, availableCount: 0, trialCount: 0, toolingNames: [] },
    materialCategories: [],
    suppliers: [],
    documents: [],
    qualityRecords: [],
    processRoutes: []
  }
}

function createTimeline(projectId = 9, nodeKey = 'PRODUCT_LINE_DESIGN_CONFIRM') {
  return {
    projectId,
    productId: projectId,
    productType: 'product_line',
    currentNode: '设计确认',
    currentStepNo: 2,
    currentConfirmed: false,
    nodes: [{
      nodeKey,
      nodeName: '设计确认',
      phaseName: '设计验证阶段',
      stepNo: 2,
      status: 'current',
      nodeStatus: 'current',
      summary: '当前节点',
      ownerRole: '工程',
      documentCount: 0,
      confirmed: false
    }]
  }
}

describe('project M4 panels', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.query = {}
    bomApi.getProjectBoms.mockResolvedValue([])
    processApi.getProjectProcessRoutes.mockResolvedValue([])
    attachmentApi.getTimelineAttachments.mockResolvedValue([])
    foundationApi.getProductPresentation.mockResolvedValue(createPresentation())
    projectApi.getProjects.mockResolvedValue([])
    projectApi.getProjectTimeline.mockResolvedValue(createTimeline())
  })

  it('loads project BOMs and distinguishes a successful empty result', async () => {
    const wrapper = mount(ProjectBomPanel, { ...mountOptions, props: { projectId: 7 } })

    await flushPromises()

    expect(bomApi.getProjectBoms).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('当前项目还没有 BOM')
    expect(wrapper.get('[data-test="bom-create"]').attributes('disabled')).toBeUndefined()
  })

  it('renders a frozen BOM as read-only', async () => {
    bomApi.getProjectBoms.mockResolvedValue([{
      productBomId: 31,
      productId: 7,
      bomCode: 'BOM-31',
      bomName: '冻结 BOM',
      bomType: 'ebom',
      versionNo: 'A',
      status: 'frozen',
      items: []
    }])

    const wrapper = mount(ProjectBomPanel, { ...mountOptions, props: { projectId: 7 } })
    await flushPromises()

    expect(wrapper.text()).toContain('已冻结')
    expect(wrapper.get('[data-test="bom-edit"]').attributes()).toHaveProperty('disabled')
    expect(wrapper.get('[data-test="bom-item-add"]').attributes()).toHaveProperty('disabled')
  })

  it('loads process routes and shows the successful empty state', async () => {
    const wrapper = mount(ProjectProcessRoutePanel, { ...mountOptions, props: { projectId: 7 } })

    await flushPromises()

    expect(processApi.getProjectProcessRoutes).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('当前项目还没有工艺路线')
  })

  it('does not query or upload attachments without a current timeline node', async () => {
    const wrapper = mount(TimelineAttachmentPanel, {
      ...mountOptions,
      props: { projectId: 7, nodeKey: null }
    })

    await flushPromises()

    expect(attachmentApi.getTimelineAttachments).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前项目没有可用的时间轴节点')
    expect(wrapper.get('[data-test="attachment-upload"]').attributes()).toHaveProperty('disabled')
  })

  it('queries attachments for the current timeline node', async () => {
    const wrapper = mount(TimelineAttachmentPanel, {
      ...mountOptions,
      props: { projectId: 7, nodeKey: 'sampling-process' }
    })

    await flushPromises()

    expect(attachmentApi.getTimelineAttachments).toHaveBeenCalledWith(7, 'sampling-process')
    expect(wrapper.text()).toContain('当前节点还没有附件')
  })

  it('does not mount hidden attachment panels before the attachment tab is active', async () => {
    routeState.query = { tab: 'archived', archiveView: 'product', productId: '9' }
    projectApi.getProjects.mockResolvedValue([createProductSummary()])

    const wrapper = mount(ProjectCenterView, {
      global: {
        plugins: [ElementPlus],
        stubs: {
          Teleport: true,
          PageContainer: { template: '<main><slot /></main>' },
          StatusTag: { template: '<span />' },
          FilePreview: { template: '<div />' },
          ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
          ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
          ElDropdownMenu: { template: '<div><slot /></div>' },
          ElDropdownItem: { template: '<button><slot /></button>' },
          ElTable: { template: '<div><slot /></div>' },
          ElTableColumn: { template: '<div />' },
          ProjectBomPanel: { template: '<div />' },
          ProjectProcessRoutePanel: { template: '<div />' },
          TimelineAttachmentPanel: {
            props: ['projectId', 'nodeKey'],
            template: '<div data-test="timeline-attachment-panel">{{ projectId }}:{{ nodeKey }}</div>'
          }
        }
      }
    })

    await flushPromises()
    await flushPromises()

    expect(wrapper.find('[data-test="timeline-attachment-panel"]').exists()).toBe(false)
  })
})
