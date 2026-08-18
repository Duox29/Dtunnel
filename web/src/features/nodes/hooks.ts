import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { nodesApi } from "./api";

export const nodeKeys = { all: ["nodes"] as const };

export function useNodes() {
  return useQuery({ queryKey: nodeKeys.all, queryFn: nodesApi.list });
}

export function useRegisterNode() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { code: string; region: string; publicAddress: string; frpsAdminUrl?: string }) =>
      nodesApi.register(vars.code, vars.region, vars.publicAddress, vars.frpsAdminUrl),
    onSuccess: () => qc.invalidateQueries({ queryKey: nodeKeys.all }),
  });
}

export function useUpdateNode() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { nodeId: string; patch: { publicAddress?: string; frpsAdminUrl?: string } }) =>
      nodesApi.update(vars.nodeId, vars.patch),
    onSuccess: () => qc.invalidateQueries({ queryKey: nodeKeys.all }),
  });
}

export function useSeedPorts() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { nodeId: string; protocol: string; start: number; end: number }) =>
      nodesApi.seedPorts(vars.nodeId, vars.protocol, vars.start, vars.end),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: nodeKeys.all });
      qc.invalidateQueries({ queryKey: ["ports"] });
    },
  });
}
