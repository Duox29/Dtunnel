export interface NodeInfo {
  id: string;
  code: string;
  region: string;
  publicAddress: string;
  status: "OFFLINE" | "ONLINE" | "DISABLED";
  protocolCapabilities: string[];
}
