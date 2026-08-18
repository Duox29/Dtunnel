export interface PortInfo {
  id: string;
  nodeId: string;
  protocol: string;
  portNumber: number;
  status: "AVAILABLE" | "RESERVED" | "ALLOCATED" | "ACTIVE" | "EXPIRED_PENDING_RELEASE" | "RELEASED" | "DISABLED";
  allocationId?: string;
  expiresAt?: string;
}
