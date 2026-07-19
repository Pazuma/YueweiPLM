import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const bomApi = vi.hoisted(() => ({
  getProjectBoms: vi.fn(), getBomWorkbench: vi.fn(), getProductionConfirmation: vi.fn(),
  confirmProductionOperations: vi.fn(), confirmProductionColors: vi.fn()
}))
const processApi = vi.hoisted(() => ({ getProjectProcessRoutes: vi.fn() }))
vi.mock('@/api/modules/bom', () => bomApi)
vi.mock('@/api/modules/process', () => processApi)

import ProductionConfirmationDialog from '../components/ProductionConfirmationDialog.vue'

describe('ProductionConfirmationDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    bomApi.getProjectBoms.mockResolvedValue([{ productBomId: 31, bomType: 'mbom', status: 'reviewing' }])
    bomApi.getBomWorkbench.mockResolvedValue({
      productBomId: 31, productId: 7, versionNo: 'V1', status: 'reviewing', routes: [
        { productBomRouteId: 81, processId: 9, routeCode: 'DYE', routeName: '染色路线', colors: ['黑色', '蓝色'], items: [], costSnapshot: { totalCost: 12.5, currencyCode: 'CNY' } }
      ]
    })
    processApi.getProjectProcessRoutes.mockResolvedValue([{ processId: 9, processName: '染色路线', operations: [
      { processId: 91, sequenceNo: 1, processName: '喷涂', status: 'confirmed' }
    ] }])
    bomApi.getProductionConfirmation.mockResolvedValue({ selectedOperationCount: 0, selectedColorCount: 0, operationProcessIds: [], colors: [] })
    bomApi.confirmProductionOperations.mockResolvedValue({ selectedOperationCount: 1 })
    bomApi.confirmProductionColors.mockResolvedValue({ selectedColorCount: 2, createdSkuCount: 2 })
  })

  it('defaults all eligible colors and confirms production colors', async () => {
    const wrapper = mount(ProductionConfirmationDialog, {
      props: { modelValue: true, projectId: 7, mode: 'colors' },
      global: { plugins: [ElementPlus], stubs: { teleport: true, transition: false } }
    })
    await vi.waitFor(() => expect(wrapper.text()).toContain('黑色'))
    expect(wrapper.text()).toContain('已选择 2 个颜色')
    await wrapper.get('[data-test="confirm-production-colors"]').trigger('click')
    await vi.waitFor(() => expect(bomApi.confirmProductionColors).toHaveBeenCalled())
    expect(bomApi.confirmProductionColors.mock.calls[0][1].colors).toHaveLength(2)
  })
})
