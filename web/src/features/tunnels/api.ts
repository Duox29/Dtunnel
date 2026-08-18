import { apiFetch } from "../../lib/api-client";
import type { TunnelInfo } from "./types";

export const tunnelsApi = {
  list: () => apiFetch<TunnelInfo[]>("GET", "/api/v1/tunnels"),
  create: (vars: { allocationId: string; agentId: string; name: string; targetHost: string; targetPort: number }) =>
    apiFetch<TunnelInfo>("POST", "/api/v1/tunnels", vars),
  start: (id: string) => apiFetch<TunnelInfo>("POST", `/api/v1/tunnels/${id}/start`),
  stop: (id: string) => apiFetch<TunnelInfo>("POST", `/api/v1/tunnels/${id}/stop`),
  remove: (id: string) => apiFetch<{ status: string }>("DELETE", `/api/v1/tunnels/${id}`),
};
