import { apiFetch } from "../../lib/api-client";
import type { ResourceRequest } from "./types";

export const requestsApi = {
  list: () => apiFetch<ResourceRequest[]>("GET", "/api/v1/resource-requests"),
  create: (vars: { nodeId: string; protocol: string; preferredPort: number | null; durationDays: number; purpose: string }) =>
    apiFetch<ResourceRequest>("POST", "/api/v1/resource-requests", vars),
  approve: (id: string) =>
    apiFetch<{ status: string; allocationId: string; expiresAt: string }>("POST", `/api/v1/resource-requests/${id}/approve`),
  reject: (id: string, reason?: string) =>
    apiFetch<ResourceRequest>("POST", `/api/v1/resource-requests/${id}/reject`, { reason }),
};
