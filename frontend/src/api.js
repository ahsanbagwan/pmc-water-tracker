const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080/api'

export async function fetchLocalities({ status, search } = {}) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (search) params.set('search', search)
  const query = params.toString() ? `?${params.toString()}` : ''

  const res = await fetch(`${API_BASE}/localities${query}`)
  if (!res.ok) {
    throw new Error(`Request failed (${res.status})`)
  }
  return res.json()
}

export async function submitReport(payload) {
  const res = await fetch(`${API_BASE}/localities/reports`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.error ?? `Request failed (${res.status})`)
  }
  return res.json()
}
