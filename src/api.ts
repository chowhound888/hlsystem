import type { VisitRecord } from './types'

export type VisitRecordListResponse = {
  items: VisitRecord[]
  total: number
  page: number
  pageSize: number
  totalPages: number
  departments: string[]
}

async function request<T>(path: string): Promise<T> {
  const response = await fetch(path)
  if (!response.ok) throw new Error(`API request failed: ${response.status}`)
  return response.json() as Promise<T>
}

export function fetchVisitRecords(params: { keyword?: string; department?: string; date?: string; page?: number; pageSize?: number }) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })
  return request<VisitRecordListResponse>(`/api/visit-records?${query.toString()}`)
}

export function fetchVisitRecord(id: string) {
  return request<{ record: VisitRecord }>(`/api/visit-records/${encodeURIComponent(id)}`)
}
