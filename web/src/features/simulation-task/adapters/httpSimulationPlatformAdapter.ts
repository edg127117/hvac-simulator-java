import { requestJson } from '../../../shared/api/httpClient'
import type { SimulationPlatformPort } from '../ports/SimulationPlatformPort'

export function createHttpSimulationPlatformAdapter(baseUrl = '/api'): SimulationPlatformPort {
  return {
    listReleases: () => requestJson(`${baseUrl}/model-releases`),
    getParameters: (version, mode) => requestJson(
      `${baseUrl}/model-releases/${encodeURIComponent(version)}/parameters?mode=${mode}`,
    ),
    createRun: (input) => requestJson(`${baseUrl}/simulation-runs`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),
    getRun: (runId) => requestJson(`${baseUrl}/simulation-runs/${runId}`),
    getSeries: (runId) => requestJson(`${baseUrl}/simulation-runs/${runId}/series`),
    createDelivery: (runId, input) => requestJson(
      `${baseUrl}/simulation-runs/${runId}/mqtt-deliveries`,
      { method: 'POST', body: JSON.stringify(input) },
    ),
    getDelivery: (runId, deliveryId) => requestJson(
      `${baseUrl}/simulation-runs/${runId}/mqtt-deliveries/${deliveryId}`,
    ),
  }
}
