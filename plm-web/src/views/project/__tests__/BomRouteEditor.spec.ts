import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'

import BomRouteEditor from '../components/BomRouteEditor.vue'

describe('BomRouteEditor', () => {
  it('adds and saves route BOM material details', async () => {
    const wrapper = mount(BomRouteEditor, {
      global: { plugins: [ElementPlus], stubs: { teleport: true } },
      props: {
        modelValue: true,
        routes: [{ processId: 9, routeCode: 'DYE', routeName: '染色路线', colors: ['蓝色'], items: [] }]
      }
    })

    await flushPromises()
    await wrapper.get('[data-test="route-item-add"]').trigger('click')
    await wrapper.get('[data-test="route-item-code"]').setValue('MAT-001')
    await wrapper.get('[data-test="route-item-name"]').setValue('蓝色色母')
    await wrapper.get('[data-test="route-item-quantity"] input').setValue('2')
    await wrapper.get('[data-test="route-item-loss-rate"] input').setValue('0.05')
    await wrapper.get('[data-test="route-item-unit-cost"] input').setValue('3.5')
    await wrapper.get('[data-test="route-editor-save"]').trigger('click')

    const saved = wrapper.emitted('save')?.[0]?.[0] as Array<{ items: Array<Record<string, unknown>> }>
    expect(saved[0].items[0]).toMatchObject({
      itemCode: 'MAT-001', itemName: '蓝色色母', quantity: 2, lossRate: 0.05, unitCost: 3.5
    })
  })
})
