// HTTP client for the control plane. Session-cookie auth for /api/v1
// (detail.md §8); JSON-only, throws ApiError with the server message.
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export async function apiFetch<T>(method: string, path: string, body?: unknown): Promise<T> {
  const base = import.meta.env.VITE_API_BASE ?? "";
  const res = await fetch(base + path, {
    method,
    credentials: "include",
    headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    const msg = (data as { error?: string } | null)?.error ?? res.statusText;
    throw new ApiError(res.status, msg);
  }
  return data as T;
}
