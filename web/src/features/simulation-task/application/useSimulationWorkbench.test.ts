import { defineComponent, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { SimulationPlatformPort } from '../ports/SimulationPlatformPort'
import { useSimulationWorkbench } from './useSimulationWorkbench'

function parameterCatalog(
  version: 'gaia-1.0' | 'gaia-1.1',
  mode: 'BASELINE' | 'SCENARIO',
  defaultValue: number,
) {
  return {
    version, displayName: version === 'gaia-1.1' ? 'Gaia 1.1' : 'Gaia 1.0', mode,
    parameters: [{
      code: 'coolingCapacityKw', label: '额定制冷量', group: '冷水机组', unit: 'kW',
      valueType: 'NUMBER' as const, defaultValue, minimum: 1, maximum: 2000,
      scope: 'COMMON' as const,
      editable: mode === 'SCENARIO', readOnlyReason: mode === 'BASELINE' ? '基准冻结' : null,
    }],
  }
}

describe('simulation workbench', () => {
  let port: SimulationPlatformPort

  beforeEach(() => {
    port = {
      listReleases: vi.fn().mockResolvedValue([
        { version: 'gaia-1.0', displayName: 'Gaia 1.0', outputFieldCount: 17 },
        { version: 'gaia-1.1', displayName: 'Gaia 1.1', outputFieldCount: 30 },
      ]),
      getParameters: vi.fn().mockImplementation((version, mode) => Promise.resolve(
        parameterCatalog(version, mode, version === 'gaia-1.0' ? 650 : mode === 'BASELINE' ? 700 : 710),
      )),
      createRun: vi.fn().mockResolvedValue({ runId: 'run-1', status: 'QUEUED' }),
      getRun: vi.fn().mockResolvedValue({
        runId: 'run-1', modelVersion: 'gaia-1.1', mode: 'SCENARIO', seed: 20240806,
        overrides: { coolingCapacityKw: 735 }, status: 'COMPLETED', completedSteps: 10080,
        totalSteps: 10080, simulationTime: '2024-07-07T23:59:00', errorCode: null,
        errorMessage: null, createdAt: '2026-08-11T00:00:00Z',
      }),
      getSeries: vi.fn().mockResolvedValue({ timestamps: [], groups: [] }),
      createDelivery: vi.fn().mockResolvedValue({ deliveryId: 'delivery-1', status: 'QUEUED' }),
      getDelivery: vi.fn().mockResolvedValue({
        deliveryId: 'delivery-1', runId: 'run-1', status: 'COMPLETED', totalMessages: 4,
        successfulMessages: 4, failedMessages: 0, firstError: null, createdAt: '2026-08-11T00:00:00Z',
      }),
    }
  })

  async function mountWorkbench() {
    let workbench!: ReturnType<typeof useSimulationWorkbench>
    mount(defineComponent({
      setup() { workbench = useSimulationWorkbench(port); return () => null },
    }))
    await flushPromises()
    return workbench
  }

  it('loads Gaia 1.1 baseline defaults first and reloads scenario defaults', async () => {
    const workbench = await mountWorkbench()
    expect(workbench.selectedVersion.value).toBe('gaia-1.1')
    expect(workbench.parameterValues.value.coolingCapacityKw).toBe(700)

    workbench.mode.value = 'SCENARIO'
    await workbench.loadCatalog()
    expect(workbench.parameterValues.value.coolingCapacityKw).toBe(710)
  })

  it('replaces all parameter values when the model version changes', async () => {
    const workbench = await mountWorkbench()
    workbench.parameterValues.value = {
      ...workbench.parameterValues.value,
      'measurement.sensorBias': 9,
    }

    workbench.selectedVersion.value = 'gaia-1.0'
    await workbench.loadCatalog()

    expect(workbench.parameterValues.value).toEqual({ coolingCapacityKw: 650 })
    expect(workbench.catalog.value?.displayName).toBe('Gaia 1.0')
  })

  it('sends edited scenario parameters and enables MQTT after a completed Gaia 1.1 run', async () => {
    const workbench = await mountWorkbench()
    workbench.mode.value = 'SCENARIO'
    await workbench.loadCatalog()
    workbench.parameterValues.value.coolingCapacityKw = 735

    await workbench.startRun()
    await nextTick()

    expect(port.createRun).toHaveBeenCalledWith(expect.objectContaining({
      modelVersion: 'gaia-1.1', mode: 'SCENARIO', overrides: { coolingCapacityKw: 735 },
    }))
    expect(workbench.canDeliverMqtt.value).toBe(true)
    expect(workbench.series.value).toEqual({ timestamps: [], groups: [] })
  })
})
