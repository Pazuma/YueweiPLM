import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ getBomLedger: vi.fn(), getBomWorkbench: vi.fn(), getBomSkus: vi.fn() }))
vi.mock('@/api/modules/bom', () => api)

import BomCenterView from '../BomCenterView.vue'

describe('BomCenterView', () => {
  beforeEach(() => {
    api.getBomLedger.mockResolvedValue([{
      productBomId: 31, productId: 7, bomCode: 'BOM-NBA-001', productCode: 'NBA4030',
      productName: '亮甲 2.0', model: null, versionNo: 'V1', routeCount: 2, skuCount: 70,
      status: 'released', sourceType: 'manual', updatedAt: '2026-07-18T10:00:00'
    }])
    api.getBomWorkbench.mockResolvedValue({
      productBomId: 31, productId: 7, bomCode: 'BOM-NBA-001', bomName: '亮甲正式 BOM',
      bomScope: 'formal', versionNo: 'V1', status: 'released', testItems: [],
      routes: [{ productBomRouteId: 81, processId: 9, routeCode: 'DYE', routeName: '染色路线', colors: ['黑色', '蓝色'], items: [], costSnapshot: null }]
    })
    api.getBomSkus.mockResolvedValue([{ productId: 9, skuCode: 'NBA4030112231', productName: '亮甲 2.0', phoneModel: 'Samsung A56', color: '蓝色', status: 'released', routeCode: 'DYE' }])
  })

  it('keeps list basic and opens detail drawer and related SKU dialog', async () => {
    const wrapper = mount(BomCenterView, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await vi.waitFor(() => expect(wrapper.text()).toContain('BOM-NBA-001'))
    expect(wrapper.text()).toContain('BOM-NBA-001')
    expect(wrapper.text()).toContain('亮甲 2.0')
    expect(wrapper.text()).toContain('关联 SKU')

    await wrapper.get('[data-test="bom-detail"]').trigger('click')
    await vi.waitFor(() => expect(api.getBomWorkbench).toHaveBeenCalledWith(31))
    expect(document.body.textContent).toContain('染色路线')

    await wrapper.get('[data-test="bom-skus"]').trigger('click')
    await vi.waitFor(() => expect(api.getBomSkus).toHaveBeenCalledWith(31))
    expect(document.body.textContent).toContain('Samsung A56')
    wrapper.unmount()
  })
})
