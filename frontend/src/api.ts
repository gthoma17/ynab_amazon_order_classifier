export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(path)
  if (!response.ok) {
    throw new Error(`GET ${path} failed with status ${response.status}`)
  }
  return response.json() as Promise<T>
}

export async function apiPost<T>(path: string): Promise<T> {
  const response = await fetch(path, { method: 'POST' })
  if (!response.ok) {
    throw new Error(`POST ${path} failed with status ${response.status}`)
  }
  return response.json() as Promise<T>
}

export async function apiPostWithBody<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw new Error(`POST ${path} failed with status ${response.status}`)
  }
  return response.json() as Promise<T>
}

export async function apiPut(path: string, body: unknown): Promise<void> {
  const response = await fetch(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw new Error(`PUT ${path} failed with status ${response.status}`)
  }
}
