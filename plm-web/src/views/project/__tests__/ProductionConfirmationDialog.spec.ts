import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const bomApi = vi.hoisted(() => ({
  getProjectBoms: vi.fn(),
  getBomWorkbench: vi.fn(),
  getProductionConfirmation: vi.fn(),
  confirmProductionRoutes: vi.fn(),
  confirmProductionColors: vi.fn()
}))
const processApi = vi.hoisted(() => ({ getProjectProcessRoutes: vi.fn() }))
vi.mock('@/api/modules/bom', () => bomApi)
vi.mock('@/api/modules/process', () => processApi)

import ProductionConfirmationDialog from '../components/ProductionConfirmationDialog.vue'

describe('ProductionConfirmationDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    bomApi.getProjectBoms.mockResolvedValue([
      { productBomId: 31, bomType: 'mbom', status: 'reviewing' },
      { productBomId: 32, bomType: 'mbom', status: 'draft' }
    ])
    bomApi.getBomWorkbench.mockImplementation((bomId: number) => Promise.resolve({
      productBomId: bomId,
      productId: 7,
      versionNo: bomId === 31 ? 'V1' : 'V2',
      status: bomId === 31 ? 'reviewing' : 'draft',
      routes: [{
        productBomRouteId: bomId === 31 ? 81 : 82,
        processId: 9,
        routeCode: 'DYE',
        routeName: '染色工艺路线',
        colors: ['Negro', 'Azul Rey'],
        colorItems: [
          { codeItemId: 2, codeValue: '02', codeName: 'Negro' },
          { codeItemId: 8, codeValue: '08', codeName: 'Azul Rey' }
        ],
        items: [{ lineNo: 1, itemName: 'TPU', quantity: 1, unit: 'pcs' }],
        costSnapshot: { totalCost: 12.5, currencyCode: 'CNY' }
      }]
    }))
    processApi.getProjectProcessRoutes.mockResolvedValue([{
      processId: 9,
      processName: '染色工艺路线',
      operations: [
        { processId: 91, sequenceNo: 1, processName: '喷涂', status: 'confirmed' },
        { processId: 92, sequenceNo: 2, processName: '包装', status: 'confirmed' }
      ]
    }])
    bomApi.getProductionConfirmation.mockResolvedValue({
      productId: 7,
      selectedOperationCount: 0,
      selectedColorCount: 0,
      createdSkuCount: 0,
      operationProcessIds: [],
      routeSelections: [{
        processId: 9,
        productBomId: 31,
        productBomRouteId: 81,
        routeName: '染色工艺路线',
        bomVersionNo: 'V1',
        operationProcessIds: [91]
      }],
      colors: []
    })
    bomApi.confirmProductionRoutes.mockResolvedValue({ selectedOperationCount: 2 })
    bomApi.confirmProductionColors.mockResolvedValue({ selectedColorCount: 2, createdSkuCount: 2 })
  })

  it('confirms one formal BOM and selected production operations per route', async () => {
    const wrapper = mount(ProductionConfirmationDialog, {
      props: { modelValue: true, projectId: 7, mode: 'operations' },
      global: { plugins: [ElementPlus], stubs: { teleport: true, transition: false } }
    })
    await vi.waitFor(() => expect(wrapper.text()).toContain('候选 BOM V1'))

    expect(wrapper.text()).toContain('染色工艺路线')
    expect(wrapper.text()).toContain('正式 BOM')
    expect(wrapper.text()).toContain('喷涂')
    await wrapper.get('[data-test="confirm-production-operations"]').trigger('click')

    await vi.waitFor(() => expect(bomApi.confirmProductionRoutes).toHaveBeenCalled())
    expect(bomApi.confirmProductionRoutes.mock.calls[0][1]).toMatchObject({
      routes: [{
        processId: 9,
        productBomId: 31,
        productBomRouteId: 81,
        operationProcessIds: [91]
      }]
    })
  })

  it('defaults colors only from confirmed formal route selections', async () => {
    const wrapper = mount(ProductionConfirmationDialog, {
      props: { modelValue: true, projectId: 7, mode: 'colors' },
      global: { plugins: [ElementPlus], stubs: { teleport: true, transition: false } }
    })
    await vi.waitFor(() => expect(wrapper.text()).toContain('02 · Negro'))

    expect(wrapper.text()).toContain('已选择 2 个颜色')
    await wrapper.get('[data-test="confirm-production-colors"]').trigger('click')

    await vi.waitFor(() => expect(bomApi.confirmProductionColors).toHaveBeenCalled())
    expect(bomApi.confirmProductionColors.mock.calls[0][1].colors).toHaveLength(2)
    expect(bomApi.confirmProductionColors.mock.calls[0][1].colors[0]).toMatchObject({
      codeItemId: 2,
      colorCode: '02',
      colorName: 'Negro',
      productBomId: 31,
      productBomRouteId: 81
    })
  })
})
