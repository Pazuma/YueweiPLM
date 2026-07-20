import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'

const api = vi.hoisted(() => ({
  getProjectBoms: vi.fn(),
  getBomWorkbench: vi.fn(),
  createProjectBom: vi.fn(),
  saveBomRoutes: vi.fn(),
  recalculateBomCosts: vi.fn(),
  submitBomReview: vi.fn(),
  freezeBom: vi.fn(),
  publishBom: vi.fn(),
  copyBomVersion: vi.fn(),
  confirmTestBom: vi.fn()
}))

const processApi = vi.hoisted(() => ({
  getProjectProcessRoutes: vi.fn()
}))

vi.mock('@/api/modules/bom', () => api)
vi.mock('@/api/modules/process', () => processApi)

import ProjectBomPanel from '../components/ProjectBomPanel.vue'

describe('ProjectBomPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
    processApi.getProjectProcessRoutes.mockResolvedValue([
      { processId: 9, processCode: 'ROUTE-DYE', processName: '染色工艺路线', operations: [] }
    ])
    api.createProjectBom.mockResolvedValue({
      productBomId: 32,
      productId: 7,
      bomName: '候选 BOM V2',
      bomType: 'mbom',
      versionNo: 'V2',
      status: 'draft',
      processId: 9
    })
    api.getProjectBoms.mockResolvedValue([{
      productBomId: 31,
      productId: 7,
      bomCode: 'BOM-31',
      bomName: '候选 BOM',
      bomType: 'mbom',
      bomScope: 'candidate',
      versionNo: 'V1',
      status: 'draft',
      processId: 9,
      routeName: '染色工艺路线',
      candidateStatus: 'draft',
      currentFormal: true,
      materialCount: 2,
      totalCost: 12.5,
      items: []
    }])
    api.getBomWorkbench.mockResolvedValue({
      productBomId: 31,
      productId: 7,
      bomCode: 'BOM-31',
      bomName: '候选 BOM',
      bomScope: 'candidate',
      versionNo: 'V1',
      status: 'draft',
      testItems: [],
      routes: [{
        productBomRouteId: 81,
        processId: 9,
        routeCode: 'DYE',
        routeName: '染色工艺路线',
        status: 'active',
        colors: ['黑色', '蓝色'],
        items: []
      }]
    })
  })

  it('shows candidate BOMs with route and current formal status', async () => {
    const wrapper = mount(ProjectBomPanel, {
      props: { projectId: 7 },
      global: { plugins: [ElementPlus], stubs: { transition: false } }
    })
    await vi.waitFor(() => expect(api.getBomWorkbench).toHaveBeenCalledWith(31))

    expect(wrapper.text()).toContain('新建 BOM')
    expect(wrapper.text()).toContain('候选 BOM')
    expect(wrapper.text()).toContain('染色工艺路线')
    expect(wrapper.text()).toContain('当前正式')
    expect(wrapper.text()).toContain('导入 XLSX')
    expect(wrapper.text()).toContain('提交审核')
    expect(wrapper.text()).toContain('冻结')
    expect(wrapper.text()).toContain('发布')
    expect(wrapper.find('[data-test="bom-create"]').exists()).toBe(true)
  })

  it('opens candidate BOM creation with required process route selection', async () => {
    const wrapper = mount(ProjectBomPanel, {
      props: { projectId: 7 },
      global: { plugins: [ElementPlus], stubs: { transition: false } }
    })
    await vi.waitFor(() => expect(processApi.getProjectProcessRoutes).toHaveBeenCalledWith(7))

    await wrapper.get('[data-test="bom-create"]').trigger('click')
    await vi.waitFor(() => expect(`${wrapper.text()}${document.body.textContent}`).toContain('关联工艺路线'))

    expect(document.body.textContent).toContain('染色工艺路线')
    expect(wrapper.find('[data-test="bom-create-submit"]').exists()).toBe(true)
  })
})
