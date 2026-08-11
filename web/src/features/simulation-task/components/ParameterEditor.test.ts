import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ParameterEditor from './ParameterEditor.vue'

const parameter = {
  code: 'coolingCapacityKw', label: '额定制冷量', group: '冷水机组', unit: 'kW',
  valueType: 'NUMBER' as const, defaultValue: 700, minimum: 1, maximum: 2000,
  editable: true, readOnlyReason: null,
}

describe('ParameterEditor', () => {
  it('keeps baseline inputs read-only', () => {
    const wrapper = mount(ParameterEditor, {
      props: { parameters: [parameter], values: { coolingCapacityKw: 700 }, editable: false, loading: false },
    })
    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('基准只读')
  })

  it('emits numeric changes in scenario mode', async () => {
    const wrapper = mount(ParameterEditor, {
      props: { parameters: [parameter], values: { coolingCapacityKw: 700 }, editable: true, loading: false },
    })
    await wrapper.get('input').setValue('735')
    expect(wrapper.emitted('update:value')).toEqual([['coolingCapacityKw', 735]])
  })
})
