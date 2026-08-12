import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ParameterEditor from './ParameterEditor.vue'

const parameter = {
  code: 'coolingCapacityKw', label: '额定制冷量', group: '冷水机组', unit: 'kW',
  valueType: 'NUMBER' as const, defaultValue: 700, minimum: 1, maximum: 2000,
  scope: 'COMMON' as const, editable: true, readOnlyReason: null,
}

const specificParameter = {
  ...parameter,
  code: 'measurement.sensorBias',
  label: '传感器统一偏差',
  group: '测量',
  scope: 'VERSION_SPECIFIC' as const,
}

describe('ParameterEditor', () => {
  it('keeps baseline inputs read-only', () => {
    const wrapper = mount(ParameterEditor, {
      props: {
        parameters: [parameter], values: { coolingCapacityKw: 700 }, editable: false,
        loading: false, versionDisplayName: 'Gaia 1.0',
      },
    })
    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('基准只读')
  })

  it('emits numeric changes in scenario mode', async () => {
    const wrapper = mount(ParameterEditor, {
      props: {
        parameters: [parameter], values: { coolingCapacityKw: 700 }, editable: true,
        loading: false, versionDisplayName: 'Gaia 1.0',
      },
    })
    await wrapper.get('input').setValue('735')
    expect(wrapper.emitted('update:value')).toEqual([['coolingCapacityKw', 735]])
  })

  it('separates common and version-specific parameters', () => {
    const wrapper = mount(ParameterEditor, {
      props: {
        parameters: [parameter, specificParameter],
        values: { coolingCapacityKw: 700, 'measurement.sensorBias': 0 },
        editable: true,
        loading: false,
        versionDisplayName: 'Gaia 1.1',
      },
    })

    expect(wrapper.text()).toContain('公共参数')
    expect(wrapper.text()).toContain('Gaia 1.1 版本专属参数')
    expect(wrapper.text()).toContain('传感器统一偏差')
  })

  it('shows an explicit empty state when the version has no specific parameters', () => {
    const wrapper = mount(ParameterEditor, {
      props: {
        parameters: [parameter], values: { coolingCapacityKw: 700 }, editable: true,
        loading: false, versionDisplayName: 'Gaia 1.0',
      },
    })

    expect(wrapper.text()).toContain('Gaia 1.0 版本专属参数')
    expect(wrapper.text()).toContain('当前版本暂无专属参数')
  })
})
