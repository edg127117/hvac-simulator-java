import { afterEach, describe, expect, it, vi } from 'vitest'
import { createHttpSimulationPlatformAdapter } from './httpSimulationPlatformAdapter'

describe('HTTP simulation platform adapter', () => {
  afterEach(() => vi.restoreAllMocks())

  it('sends scenario overrides to the run API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ runId: 'run-1', status: 'QUEUED' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ))

    await createHttpSimulationPlatformAdapter('/api').createRun({
      modelVersion: 'gaia-1.1',
      mode: 'SCENARIO',
      seed: 42,
      overrides: { coolingCapacityKw: 999 },
    })

    expect(fetchMock).toHaveBeenCalledWith('/api/simulation-runs', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        modelVersion: 'gaia-1.1', mode: 'SCENARIO', seed: 42,
        overrides: { coolingCapacityKw: 999 },
      }),
    }))
  })

  it('retains the Chinese server error meaning', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ code: 'INVALID_PARAMETER', title: '参数无效', message: '冷量超出范围。' }),
      { status: 400, headers: { 'Content-Type': 'application/json' } },
    ))

    await expect(createHttpSimulationPlatformAdapter().listReleases()).rejects.toMatchObject({
      code: 'INVALID_PARAMETER', title: '参数无效', message: '冷量超出范围。',
    })
  })

  it('sends selected central HVAC targets and tower identity', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ deliveryId: 'delivery-1', status: 'QUEUED' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ))
    const input = {
      fromStep: 704,
      toStep: 740,
      timeMode: 'REBASE_TO_NOW' as const,
      buildingId: 'BLD001',
      deviceId: 'WCR1',
      coolingTowerDeviceId: 'TOWER1',
      targets: ['WCR_COP', 'TOWER_EFF'] as const,
    }

    await createHttpSimulationPlatformAdapter('/api').createDelivery('run-1', input)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/simulation-runs/run-1/mqtt-deliveries',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(input) }),
    )
  })
})
