import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MqttDeliveryPanel from './MqttDeliveryPanel.vue'

describe('MqttDeliveryPanel', () => {
  it('selects COP and tower efficiency with their real device defaults', async () => {
    const wrapper = mount(MqttDeliveryPanel, {
      props: { enabled: true, delivering: false, delivery: null },
    })

    expect(wrapper.get('input[value="WCR_COP"]').element).toMatchObject({ checked: true })
    expect(wrapper.get('input[value="TOWER_EFF"]').element).toMatchObject({ checked: true })
    expect((wrapper.get('input[aria-label="冷水机组设备编号"]').element as HTMLInputElement).value)
      .toBe('WCR1')
    expect((wrapper.get('input[aria-label="冷却塔设备编号"]').element as HTMLInputElement).value)
      .toBe('TOWER1')

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('deliver')).toEqual([[{
      fromStep: 0,
      toStep: 59,
      timeMode: 'REBASE_TO_NOW',
      buildingId: 'BLD001',
      deviceId: 'WCR1',
      coolingTowerDeviceId: 'TOWER1',
      targets: ['WCR_COP', 'TOWER_EFF'],
    }]])
  })

  it('requires at least one target', async () => {
    const wrapper = mount(MqttDeliveryPanel, {
      props: { enabled: true, delivering: false, delivery: null },
    })

    await wrapper.get('input[value="WCR_COP"]').setValue(false)
    await wrapper.get('input[value="TOWER_EFF"]').setValue(false)

    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('至少选择一个指标')
  })
})
