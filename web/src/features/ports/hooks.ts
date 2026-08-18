import { useQuery } from "@tanstack/react-query";
import { portsApi } from "./api";

export const portKeys = { all: ["ports"] as const };

export function usePorts() {
  return useQuery({ queryKey: portKeys.all, queryFn: portsApi.list });
}
