import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const processApi = vi.hoisted(() => ({
  getProcessCenterSnapshot: vi.fn(),
  getProcessOperationMasters: vi.fn(),
  getProcessRouteTemplates: vi.fn(),
  confirmProcessOperationMaster: vi.fn(),
  createProcessOperationMaster: vi.fn()
}))
const bomApi = vi.hoisted(() => ({ getProcessRouteSkus: vi.fn() }))

vi.mock('@/api/modules/process', () => processApi)
vi.mock('@/api/modules/bom', () => bomApi)
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() })
}))

import ProcessCenterView from '../ProcessCenterView.vue'

describe('ProcessCenterView', () => {
  beforeEach(() => {
    processApi.getProcessOperationMasters.mockResolvedValue([])
    processApi.getProcessRouteTemplates.mockResolvedValue([])
    processApi.getProcessCenterSnapshot.mockResolvedValue({
      metrics: [], templates: [], routeDetails: {},
      routes: [{
        routeId: 81, routeCode: 'DYE', routeName: '染色工艺路线', productId: 7,
        productCode: 'NBA4030', productName: '亮甲 2.0', versionNo: 'V1',
        routeType: 'new_product_line', status: 'released', templateSource: 'standard',
        owner: '张工', operationCount: 5, totalCost: 2.3, currentGate: '已发布', riskLevel: 'low',
        hasExternalOperation: false, hasDifferenceOperation: false, targetPath: '/products/7'
      }]
    })
    bomApi.getProcessRouteSkus.mockResolvedValue([{
      productId: 9, skuCode: 'NBA4030112231', productName: '亮甲 2.0',
      phoneModel: 'Samsung A56', color: '蓝色', status: 'released', routeCode: 'DYE'
    }])
  })

  it('opens the related SKU ledger for a product route', async () => {
    const wrapper = mount(ProcessCenterView, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await wrapper.findAll('.el-segmented__item')[2].trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('亮甲 2.0'))
    await wrapper.get('[data-test="process-route-skus"]').trigger('click')

    await vi.waitFor(() => expect(bomApi.getProcessRouteSkus).toHaveBeenCalledWith(81))
    expect(document.body.textContent).toContain('关联 SKU - DYE')
    expect(document.body.textContent).toContain('NBA4030112231')
    expect(document.body.textContent).toContain('Samsung A56')
    expect(document.body.textContent).toContain('蓝色')
    wrapper.unmount()
  })
})
