import type { ApiErrorBody } from '../../features/simulation-task/model/types'

export class PlatformApiError extends Error {
  constructor(
    public readonly code: string,
    public readonly title: string,
    message: string,
  ) {
    super(message)
  }
}

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })
  } catch {
    throw new PlatformApiError('NETWORK_ERROR', '无法连接仿真服务', '请确认后端已启动后重试。')
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as ApiErrorBody
    throw new PlatformApiError(
      body.code ?? `HTTP_${response.status}`,
      body.title ?? '请求失败',
      body.message ?? '服务未返回可识别的错误信息。',
    )
  }
  return response.json() as Promise<T>
}
