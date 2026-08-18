import { apiFetch } from "../../lib/api-client";
import type { AgentInfo } from "./types";

export const agentsApi = {
  list: () => apiFetch<AgentInfo[]>("GET", "/api/v1/agents"),
  approve: (id: string) => apiFetch<AgentInfo>("POST", `/api/v1/agents/${id}/approve`),
  revoke: (id: string) => apiFetch<AgentInfo>("POST", `/api/v1/agents/${id}/revoke`),
};
