import { computed, onMounted, ref } from 'vue'
import type { SimulationPlatformPort } from '../ports/SimulationPlatformPort'
import type {
  DeliveryView,
  ModelRelease,
  ParameterCatalog,
  RunView,
  SeriesResponse,
  SimulationMode,
} from '../model/types'
import { PlatformApiError } from '../../../shared/api/httpClient'

const TERMINAL_RUN_STATUSES = new Set(['COMPLETED', 'FAILED'])
const TERMINAL_DELIVERY_STATUSES = new Set(['COMPLETED', 'PARTIAL_FAILED', 'FAILED'])

export function useSimulationWorkbench(platform: SimulationPlatformPort) {
  const releases = ref<ModelRelease[]>([])
  const selectedVersion = ref('')
  const mode = ref<SimulationMode>('BASELINE')
  const catalog = ref<ParameterCatalog | null>(null)
  const parameterValues = ref<Record<string, number>>({})
  const seed = ref(20240806)
  const run = ref<RunView | null>(null)
  const series = ref<SeriesResponse | null>(null)
  const delivery = ref<DeliveryView | null>(null)
  const loadingCatalog = ref(false)
  const running = ref(false)
  const delivering = ref(false)
  const error = ref<{ title: string; message: string } | null>(null)

  const editable = computed(() => mode.value === 'SCENARIO')
  const canDeliverMqtt = computed(() => selectedVersion.value === 'gaia-1.1' && run.value?.status === 'COMPLETED')

  function showError(cause: unknown) {
    if (cause instanceof PlatformApiError) {
      error.value = { title: cause.title, message: cause.message }
      return
    }
    error.value = { title: '操作失败', message: cause instanceof Error ? cause.message : '发生未知错误。' }
  }

  async function loadCatalog() {
    if (!selectedVersion.value) return
    loadingCatalog.value = true
    error.value = null
    run.value = null
    series.value = null
    delivery.value = null
    try {
      catalog.value = await platform.getParameters(selectedVersion.value, mode.value)
      parameterValues.value = Object.fromEntries(
        catalog.value.parameters.map((parameter) => [parameter.code, parameter.defaultValue]),
      )
    } catch (cause) {
      showError(cause)
    } finally {
      loadingCatalog.value = false
    }
  }

  async function initialize() {
    try {
      releases.value = await platform.listReleases()
      selectedVersion.value = releases.value.find((release) => release.version === 'gaia-1.1')?.version
        ?? releases.value[0]?.version
        ?? ''
      await loadCatalog()
    } catch (cause) {
      showError(cause)
    }
  }

  async function pollRun(runId: string) {
    for (;;) {
      const latest = await platform.getRun(runId)
      run.value = latest
      if (TERMINAL_RUN_STATUSES.has(latest.status)) return latest
      await new Promise((resolve) => window.setTimeout(resolve, 250))
    }
  }

  async function startRun() {
    if (!catalog.value) return
    running.value = true
    error.value = null
    delivery.value = null
    series.value = null
    try {
      const overrides = editable.value ? { ...parameterValues.value } : {}
      const created = await platform.createRun({
        modelVersion: selectedVersion.value,
        mode: mode.value,
        seed: seed.value,
        overrides,
      })
      run.value = {
        runId: created.runId,
        modelVersion: selectedVersion.value,
        mode: mode.value,
        seed: seed.value,
        overrides,
        status: created.status,
        completedSteps: 0,
        totalSteps: 10080,
        simulationTime: null,
        errorCode: null,
        errorMessage: null,
        createdAt: new Date().toISOString(),
      }
      const completed = await pollRun(created.runId)
      if (completed.status === 'FAILED') {
        throw new PlatformApiError(
          completed.errorCode ?? 'SIMULATION_FAILED',
          '仿真运行失败',
          completed.errorMessage ?? '模型未返回错误详情。',
        )
      }
      series.value = await platform.getSeries(created.runId)
    } catch (cause) {
      showError(cause)
    } finally {
      running.value = false
    }
  }

  async function pollDelivery(runId: string, deliveryId: string) {
    for (;;) {
      const latest = await platform.getDelivery(runId, deliveryId)
      delivery.value = latest
      if (TERMINAL_DELIVERY_STATUSES.has(latest.status)) return
      await new Promise((resolve) => window.setTimeout(resolve, 250))
    }
  }

  async function startDelivery(input: {
    fromStep: number
    toStep: number
    timeMode: 'ORIGINAL' | 'REBASE_TO_NOW'
    buildingId: string
    deviceId: string
  }) {
    if (!run.value || !canDeliverMqtt.value) return
    delivering.value = true
    error.value = null
    try {
      const created = await platform.createDelivery(run.value.runId, input)
      await pollDelivery(run.value.runId, created.deliveryId)
    } catch (cause) {
      showError(cause)
    } finally {
      delivering.value = false
    }
  }

  function dismissError() {
    error.value = null
  }

  onMounted(initialize)

  return {
    releases, selectedVersion, mode, catalog, parameterValues, seed, run, series, delivery,
    loadingCatalog, running, delivering, editable, canDeliverMqtt, error,
    loadCatalog, startRun, startDelivery, dismissError,
  }
}
