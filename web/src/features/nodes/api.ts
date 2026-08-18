import { apiFetch } from "../../lib/api-client";
import type { NodeInfo } from "./types";

export const nodesApi = {
  list: () => apiFetch<NodeInfo[]>("GET", "/api/v1/nodes"),
  register: (code: string, region: string, publicAddress: string) =>
    apiFetch<NodeInfo>("POST", "/api/v1/nodes", { code, region, publicAddress }),
  seedPorts: (nodeId: string, protocol: string, start: number, end: number) =>
    apiFetch<{ created: number }>("POST", `/api/v1/nodes/${nodeId}/ports/seed`, { protocol, start, end }),
  ping: (nodeId: string) =>
    apiFetch<{ reachable: boolean; latencyMs?: number; error?: string }>("POST", `/api/v1/nodes/${nodeId}/ping`),
};
