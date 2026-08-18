export interface NodeInfo {
  id: string;
  code: string;
  region: string;
  publicAddress: string;
  /** Base URL of the node's frps admin API (usage metering). */
  frpsAdminUrl?: string | null;
  /** Shared frps vhost HTTP port for domain-routed tunnels (§3.6). */
  vhostHttpPort?: number | null;
  status: "OFFLINE" | "ONLINE" | "DISABLED";
  protocolCapabilities: string[];
}
