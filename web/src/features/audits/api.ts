import { apiFetch } from "../../lib/api-client";
import type { AuditEntry } from "./types";

export const auditsApi = {
  list: (page = 0, size = 50) =>
    apiFetch<AuditEntry[]>("GET", `/api/v1/audits?page=${page}&size=${size}`),
};
