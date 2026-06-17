const BASE = ''

export class AuthRequiredError extends Error {
  status = 401
  constructor(message = '未登录或登录已过期') {
    super(message)
  }
}

export async function api(method: string, path: string, body?: any, isForm?: boolean): Promise<any> {
  const headers: Record<string, string> = {}
  const opts: RequestInit = { method, headers, credentials: 'same-origin' }
  if (isForm) {
    opts.body = body
  } else if (body) {
    headers['Content-Type'] = 'application/json'
    opts.body = JSON.stringify(body)
  }
  const resp = await fetch(BASE + path, opts)
  let data: any
  try {
    data = await resp.json()
  } catch {
    throw new Error(`请求失败：HTTP ${resp.status}`)
  }
  if (resp.status === 401) {
    window.dispatchEvent(new CustomEvent('auth-required'))
    throw new AuthRequiredError(data.error || '未登录或登录已过期')
  }
  if (!data.ok) {
    throw new Error(data.error || '请求失败')
  }
  return data
}

export async function download(path: string, fallbackName: string) {
  const resp = await fetch(BASE + path, { credentials: 'same-origin' })
  if (resp.status === 401) {
    window.dispatchEvent(new CustomEvent('auth-required'))
    throw new AuthRequiredError()
  }
  if (!resp.ok) {
    try {
      const data = await resp.json()
      throw new Error(data.error || `下载失败：HTTP ${resp.status}`)
    } catch (error: any) {
      throw new Error(error?.message || `下载失败：HTTP ${resp.status}`)
    }
  }
  const blob = await resp.blob()
  const header = resp.headers.get('Content-Disposition') || ''
  const match = header.match(/filename\*=UTF-8''([^;]+)/)
  const name = match ? decodeURIComponent(match[1]) : fallbackName
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export const authApi = {
  login: (password: string) => api('POST', '/api/auth/login', { password }),
  logout: () => api('POST', '/api/auth/logout'),
  me: () => api('GET', '/api/auth/me'),
}

export const recordApi = {
  list: (params: Record<string, string>) => api('GET', '/api/records?' + new URLSearchParams(params).toString()),
  get: (id: number) => api('GET', `/api/records/id/${id}`),
  create: (data: any) => api('POST', '/api/records', data),
  update: (id: number, data: any) => api('PUT', `/api/records/${id}`, data),
  delete: (id: number) => api('DELETE', `/api/records/${id}`),
  review: (id: number, note: string) => api('POST', `/api/records/${id}/review`, { review_note: note }),
  unreviewed: () => api('GET', '/api/records/unreviewed'),
  unreviewedList: (limit = 500) => api('GET', `/api/records/unreviewed/list?limit=${limit}`),
  export: (params: Record<string, string>) => api('GET', '/api/records/export?' + new URLSearchParams(params).toString()),
}

export const imageApi = {
  list: (params: Record<string, string>) => api('GET', '/api/images?' + new URLSearchParams(params).toString()),
  get: (id: number) => api('GET', `/api/images/${id}`),
  update: (id: number, data: any) => api('PUT', `/api/images/${id}`, data),
  delete: (id: number) => api('DELETE', `/api/images/${id}`),
  thumbnail: (id: number) => api('GET', `/api/images/${id}/thumbnail`),
  reocr: (id: number) => api('POST', `/api/images/${id}/reocr`),
  rereview: (id: number) => api('POST', `/api/images/${id}/rereview`),
  export: (params: Record<string, string>) => api('GET', '/api/images/export?' + new URLSearchParams(params).toString()),
}

export const ocrApi = {
  scan: (formData: FormData) => api('POST', '/api/ocr/scan', formData, true),
  status: (recordId: number) => api('GET', `/api/ocr/status?record_id=${recordId}`),
}

export const rateApi = {
  list: () => api('GET', '/api/rates'),
  lookup: (origin: string, destination: string, date: string) =>
    api('GET', `/api/rates/lookup?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}&date=${encodeURIComponent(date)}`),
  create: (data: any) => api('POST', '/api/rates', data),
  update: (id: number, data: any) => api('PUT', `/api/rates/${id}`, data),
  delete: (id: number) => api('DELETE', `/api/rates/${id}`),
}

export const collectionApi = {
  all: () => api('GET', '/api/collections/all'),
  list: (category: string) => api('GET', `/api/collections?category=${category}`),
  create: (category: string, value: string) => api('POST', '/api/collections', { category, value }),
  update: (id: number, value: string) => api('PUT', `/api/collections/${id}`, { value }),
  delete: (id: number) => api('DELETE', `/api/collections/${id}`),
}

export const reportApi = {
  monthly: (params: Record<string, string>) => api('GET', '/api/report/monthly?' + new URLSearchParams(params).toString()),
}

export const dataQualityApi = {
  check: () => api('GET', '/api/data-quality'),
}
