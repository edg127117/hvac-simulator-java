import type {
  DeliveryView,
  ModelRelease,
  ParameterCatalog,
  RunView,
  SeriesResponse,
  SimulationMode,
} from '../model/types'

export interface SimulationPlatformPort {
  listReleases(): Promise<ModelRelease[]>
  getParameters(version: string, mode: SimulationMode): Promise<ParameterCatalog>
  createRun(input: {
    modelVersion: string
    mode: SimulationMode
    seed: number
    overrides: Record<string, number>
  }): Promise<{ runId: string; status: RunView['status'] }>
  getRun(runId: string): Promise<RunView>
  getSeries(runId: string): Promise<SeriesResponse>
  createDelivery(runId: string, input: {
    fromStep: number
    toStep: number
    timeMode: 'ORIGINAL' | 'REBASE_TO_NOW'
    buildingId: string
    deviceId: string
  }): Promise<{ deliveryId: string; status: DeliveryView['status'] }>
  getDelivery(runId: string, deliveryId: string): Promise<DeliveryView>
}
