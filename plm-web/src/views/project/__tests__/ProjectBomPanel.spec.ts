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
  saveTestBom: vi.fn(),
  confirmTestBom: vi.fn(),
  inheritBom: vi.fn()
}))

vi.mock('@/api/modules/bom', () => api)

import ProjectBomPanel from '../components/ProjectBomPanel.vue'

describe('ProjectBomPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.getProjectBoms.mockResolvedValue([{ productBomId: 31, productId: 7, bomCode: 'BOM-31', bomName: '正式 BOM', bomType: 'mbom', versionNo: 'V1', status: 'draft', items: [] }])
    api.getBomWorkbench.mockResolvedValue({
      productBomId: 31,
      productId: 7,
      bomCode: 'BOM-31',
      bomName: '正式 BOM',
      bomScope: 'formal',
      versionNo: 'V1',
      status: 'draft',
      testItems: [],
      routes: [{ productBomRouteId: 81, processId: 9, routeCode: 'DYE', routeName: '染色路线', status: 'active', colors: ['黑色', '蓝色'], items: [] }]
    })
  })

  it('shows test and formal modes with import route colors and lifecycle actions', async () => {
    const wrapper = mount(ProjectBomPanel, {
      props: { projectId: 7 },
      global: { plugins: [ElementPlus], stubs: { transition: false } }
    })
    await vi.waitFor(() => expect(api.getBomWorkbench).toHaveBeenCalledWith(31))

    expect(wrapper.text()).toContain('测试 BOM')
    expect(wrapper.text()).toContain('正式 BOM')
    expect(wrapper.text()).toContain('导入 XLSX')
    expect(wrapper.text()).toContain('染色路线')
    expect(wrapper.text()).toContain('黑色')
    expect(wrapper.text()).toContain('提交审核')
    expect(wrapper.text()).toContain('冻结')
    expect(wrapper.text()).toContain('发布')
    expect(wrapper.find('[data-test="bom-create"]').exists()).toBe(true)
  })
})
