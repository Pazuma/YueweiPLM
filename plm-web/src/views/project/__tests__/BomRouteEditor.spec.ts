import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'

const codeApi = vi.hoisted(() => ({ getEnabledColorCodes: vi.fn() }))
const inventoryApi = vi.hoisted(() => ({ lookupMaterialByCode: vi.fn() }))
vi.mock('@/api/modules/code', () => codeApi)
vi.mock('@/api/modules/inventory', () => inventoryApi)

import BomRouteEditor from '../components/BomRouteEditor.vue'

describe('BomRouteEditor', () => {
  it('adds and saves route BOM material details', async () => {
    codeApi.getEnabledColorCodes.mockResolvedValue([{ codeItemId: 8, codeType: 'color', codeValue: '08', codeName: 'Azul Rey', status: 'enabled', sortOrder: 8 }])
    inventoryApi.lookupMaterialByCode.mockResolvedValue({
      matched: true,
      inventoryId: 88,
      inventoryCode: 'MAT-001',
      inventoryName: '蓝色色母',
      specification: '25kg / 包',
      unit: 'kg',
      supplierName: '东莞塑胶 A',
      unitCost: 3.5,
      currencyCode: 'CNY'
    })
    const wrapper = mount(BomRouteEditor, {
      global: { plugins: [ElementPlus], stubs: { teleport: true } },
      props: {
        modelValue: true,
        routes: [{ processId: 9, routeCode: 'DYE', routeName: '染色路线', colors: ['Azul Rey'], colorItems: [{ codeItemId: 8, codeValue: '08', codeName: 'Azul Rey' }], items: [] }]
      }
    })

    await flushPromises()
    expect(codeApi.getEnabledColorCodes).toHaveBeenCalled()
    expect(wrapper.text()).toContain('08 · Azul Rey')
    await wrapper.get('[data-test="route-item-add"]').trigger('click')
    await wrapper.get('[data-test="route-item-code"]').setValue('MAT-001')
    await wrapper.get('[data-test="route-item-code"]').trigger('blur')
    await flushPromises()
    await wrapper.get('[data-test="route-item-quantity"] input').setValue('2')
    await wrapper.get('[data-test="route-item-loss-rate"] input').setValue('0.05')
    await wrapper.get('[data-test="route-editor-save"]').trigger('click')

    const saved = wrapper.emitted('save')?.[0]?.[0] as Array<{ colorItems: Array<Record<string, unknown>>; items: Array<Record<string, unknown>> }>
    expect(saved[0].colorItems[0]).toMatchObject({ codeItemId: 8, codeValue: '08', codeName: 'Azul Rey' })
    expect(saved[0].items[0]).toMatchObject({
      inventoryId: 88,
      itemCode: 'MAT-001',
      itemName: '蓝色色母',
      specification: '25kg / 包',
      quantity: 2,
      unit: 'kg',
      supplierName: '东莞塑胶 A',
      lossRate: 0.05,
      unitCost: 3.5,
      lineCost: 7,
      materialSource: 'inventory',
      unmatchedFlag: 0
    })
  })

  it('keeps unmatched material as manual row and saves supplier and line cost', async () => {
    codeApi.getEnabledColorCodes.mockResolvedValue([{ codeItemId: 8, codeType: 'color', codeValue: '08', codeName: 'Azul Rey', status: 'enabled', sortOrder: 8 }])
    inventoryApi.lookupMaterialByCode.mockResolvedValue({
      matched: false,
      inventoryCode: 'MAT-404',
      message: '物料编码未匹配到物料库，可先人工录入候选 BOM，正式发布前请确认物料资料。'
    })
    const wrapper = mount(BomRouteEditor, {
      global: { plugins: [ElementPlus], stubs: { teleport: true } },
      props: {
        modelValue: true,
        routes: [{ processId: 9, routeCode: 'DYE', routeName: '染色路线', colors: ['Azul Rey'], colorItems: [{ codeItemId: 8, codeValue: '08', codeName: 'Azul Rey' }], items: [] }]
      }
    })

    await flushPromises()
    await wrapper.get('[data-test="route-item-add"]').trigger('click')
    await wrapper.get('[data-test="route-item-code"]').setValue('MAT-404')
    await wrapper.get('[data-test="route-item-code"]').trigger('blur')
    await flushPromises()
    await wrapper.get('[data-test="route-item-name"]').setValue('人工录入辅料')
    await wrapper.get('[data-test="route-item-unit"]').setValue('PCS')
    await wrapper.get('[data-test="route-item-unit-cost"] input').setValue('2.5')
    await wrapper.get('[data-test="route-editor-save"]').trigger('click')

    expect(wrapper.text()).toContain('未匹配')
    const saved = wrapper.emitted('save')?.[0]?.[0] as Array<{ items: Array<Record<string, unknown>> }>
    expect(saved[0].items[0]).toMatchObject({
      itemCode: 'MAT-404',
      itemName: '人工录入辅料',
      unitCost: 2.5,
      lineCost: 2.5,
      materialSource: 'manual',
      unmatchedFlag: 1
    })
  })
})
