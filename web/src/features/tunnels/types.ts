export interface TunnelUsage {
  tunnelId: string;
  bytesIn: number;
  bytesOut: number;
}

export interface UsageDay {
  day: string;
  bytesIn: number;
  bytesOut: number;
}

export interface UsageHistory {
  tunnelId: string;
  days: UsageDay[];
}

export interface TunnelInfo {
  id: string;
  name: string;
  /** PORT = dedicated-port tcp/udp; HTTP = domain-routed (detail.md §3.6). */
  type: "PORT" | "HTTP";
  agentId: string;
  allocationId?: string | null;
  nodeId?: string | null;
  /** Public domain for HTTP tunnels. */
  domain?: string | null;
  targetHost: string;
  targetPort: number;
  bandwidthLimitMbps?: number | null;
  maxConnections?: number | null;
  status: "CREATED" | "CONFIGURED" | "STARTING" | "ACTIVE" | "STOPPING" | "STOPPED" | "ERROR" | "EXPIRING" | "EXPIRED";
}
