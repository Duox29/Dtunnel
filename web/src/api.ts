// Thin fetch client for the control plane /api/v1 (session-cookie auth).
const BASE = import.meta.env.VITE_API_BASE ?? "";

async function req<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method,
    credentials: "include",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(data?.error ?? res.statusText);
  return data as T;
}

export interface Me { id: string; email: string; role: string; plan: string }
export interface NodeInfo { id: string; code: string; region: string; publicAddress: string; status: string; protocolCapabilities: string[] }
export interface PortInfo { id: string; nodeId: string; protocol: string; portNumber: number; status: string; allocationId?: string; expiresAt?: string }
export interface ResourceRequest { id: string; userId: string; nodeId: string; protocol: string; preferredPort?: number; durationDays: number; purpose?: string; status: string; createdAt: string }
export interface AgentInfo { id: string; userId: string; publicKey: string; platform: string; status: string; lastSeenAt?: string }
export interface TunnelInfo { id: string; name: string; agentId: string; allocationId: string; targetHost: string; targetPort: number; status: string }

export const api = {
  me: () => req<Me>("GET", "/api/v1/auth/me"),
  login: (email: string, password: string) => req<Me>("POST", "/api/v1/auth/login", { email, password }),
  register: (email: string, password: string) => req<Me>("POST", "/api/v1/auth/register", { email, password }),
  logout: () => req("POST", "/api/v1/auth/logout"),

  nodes: () => req<NodeInfo[]>("GET", "/api/v1/nodes"),
  registerNode: (code: string, region: string, publicAddress: string) =>
    req<NodeInfo>("POST", "/api/v1/nodes", { code, region, publicAddress }),
  seedPorts: (nodeId: string, protocol: string, start: number, end: number) =>
    req<{ created: number }>("POST", `/api/v1/nodes/${nodeId}/ports/seed`, { protocol, start, end }),

  requests: () => req<ResourceRequest[]>("GET", "/api/v1/resource-requests"),
  createRequest: (nodeId: string, protocol: string, preferredPort: number | null, durationDays: number, purpose: string) =>
    req<ResourceRequest>("POST", "/api/v1/resource-requests", { nodeId, protocol, preferredPort, durationDays, purpose }),
  approveRequest: (id: string) => req("POST", `/api/v1/resource-requests/${id}/approve`),
  rejectRequest: (id: string) => req("POST", `/api/v1/resource-requests/${id}/reject`, {}),

  ports: () => req<PortInfo[]>("GET", "/api/v1/ports"),
  agents: () => req<AgentInfo[]>("GET", "/api/v1/agents"),
  approveAgent: (id: string) => req("POST", `/api/v1/agents/${id}/approve`),
  revokeAgent: (id: string) => req("POST", `/api/v1/agents/${id}/revoke`),

  tunnels: () => req<TunnelInfo[]>("GET", "/api/v1/tunnels"),
  createTunnel: (allocationId: string, agentId: string, name: string, targetHost: string, targetPort: number) =>
    req<TunnelInfo>("POST", "/api/v1/tunnels", { allocationId, agentId, name, targetHost, targetPort }),
  startTunnel: (id: string) => req("POST", `/api/v1/tunnels/${id}/start`),
  stopTunnel: (id: string) => req("POST", `/api/v1/tunnels/${id}/stop`),
};
