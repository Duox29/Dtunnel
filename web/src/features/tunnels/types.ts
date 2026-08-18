export interface TunnelInfo {
  id: string;
  name: string;
  agentId: string;
  allocationId: string;
  targetHost: string;
  targetPort: number;
  bandwidthLimitMbps?: number | null;
  maxConnections?: number | null;
  status: "CREATED" | "CONFIGURED" | "STARTING" | "ACTIVE" | "STOPPING" | "STOPPED" | "ERROR" | "EXPIRING" | "EXPIRED";
}
