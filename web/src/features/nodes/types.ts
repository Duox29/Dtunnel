export interface NodeInfo {
  id: string;
  code: string;
  region: string;
  publicAddress: string;
  /** Base URL of the node's frps admin API (usage metering). */
  frpsAdminUrl?: string | null;
  status: "OFFLINE" | "ONLINE" | "DISABLED";
  protocolCapabilities: string[];
}
