export interface AuditEntry {
  id: number;
  actor: string;
  actorType: string;
  action: string;
  resourceType?: string | null;
  resourceId?: string | null;
  result: string;
  sourceIp?: string | null;
  metadata?: Record<string, unknown> | null;
  createdAt: string;
}
