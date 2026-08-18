export interface AgentInfo {
  id: string;
  userId: string;
  publicKey: string;
  platform: string;
  agentVersion?: string | null;
  status: "PENDING" | "ONLINE" | "OFFLINE" | "REVOKED";
  lastSeenAt?: string | null;
  createdAt: string;
}
