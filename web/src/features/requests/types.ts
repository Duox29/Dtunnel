export interface ResourceRequest {
  id: string;
  userId: string;
  nodeId: string;
  protocol: string;
  preferredPort?: number | null;
  durationDays: number;
  purpose?: string | null;
  status: "DRAFT" | "SUBMITTED" | "PENDING" | "APPROVED" | "REJECTED" | "ALLOCATED";
  createdAt: string;
}
