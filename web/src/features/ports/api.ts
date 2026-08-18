import { apiFetch } from "../../lib/api-client";
import type { PortInfo } from "./types";

export const portsApi = {
  list: () => apiFetch<PortInfo[]>("GET", "/api/v1/ports"),
};
