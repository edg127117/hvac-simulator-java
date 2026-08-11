export type SimulationMode = 'BASELINE' | 'SCENARIO'
export type RunStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export type DeliveryStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'PARTIAL_FAILED' | 'FAILED'

export interface ModelRelease {
  version: string
  displayName: string
  outputFieldCount: number
}

export interface ModelParameter {
  code: string
  label: string
  group: string
  unit: string
  valueType: 'NUMBER' | 'INTEGER'
  defaultValue: number
  minimum: number
  maximum: number
  editable: boolean
  readOnlyReason: string | null
}

export interface ParameterCatalog {
  version: string
  displayName: string
  mode: SimulationMode
  parameters: ModelParameter[]
}

export interface RunView {
  runId: string
  modelVersion: string
  mode: SimulationMode
  seed: number
  overrides: Record<string, number>
  status: RunStatus
  completedSteps: number
  totalSteps: number
  simulationTime: string | null
  errorCode: string | null
  errorMessage: string | null
  createdAt: string
}

export interface ResultSeries {
  code: string
  label: string
  values: number[]
}

export interface ResultSeriesGroup {
  code: string
  title: string
  unit: string
  series: ResultSeries[]
}

export interface SeriesResponse {
  timestamps: string[]
  groups: ResultSeriesGroup[]
}

export interface DeliveryView {
  deliveryId: string
  runId: string
  status: DeliveryStatus
  totalMessages: number
  successfulMessages: number
  failedMessages: number
  firstError: string | null
  createdAt: string
}

export interface ApiErrorBody {
  code?: string
  title?: string
  message?: string
}
