import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'

const codeApi = vi.hoisted(() => ({ getEnabledColorCodes: vi.fn() }))
vi.mock('@/api/modules/code', () => codeApi)

import BomRouteEditor from '../components/BomRouteEditor.vue'

describe('BomRouteEditor', () => {
  it('adds and saves route BOM material details', async () => {
    codeApi.getEnabledColorCodes.mockResolvedValue([{ codeItemId: 8, codeType: 'color', codeValue: '08', codeName: 'Azul Rey', status: 'enabled', sortOrder: 8 }])
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
    await wrapper.get('[data-test="route-item-name"]').setValue('蓝色色母')
    await wrapper.get('[data-test="route-item-quantity"] input').setValue('2')
    await wrapper.get('[data-test="route-item-loss-rate"] input').setValue('0.05')
    await wrapper.get('[data-test="route-item-unit-cost"] input').setValue('3.5')
    await wrapper.get('[data-test="route-editor-save"]').trigger('click')

    const saved = wrapper.emitted('save')?.[0]?.[0] as Array<{ colorItems: Array<Record<string, unknown>>; items: Array<Record<string, unknown>> }>
    expect(saved[0].colorItems[0]).toMatchObject({ codeItemId: 8, codeValue: '08', codeName: 'Azul Rey' })
    expect(saved[0].items[0]).toMatchObject({
      itemCode: 'MAT-001', itemName: '蓝色色母', quantity: 2, lossRate: 0.05, unitCost: 3.5
    })
  })
})
