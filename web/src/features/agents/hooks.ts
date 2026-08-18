import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { agentsApi } from "./api";

export const agentKeys = { all: ["agents"] as const };

export function useAgents() {
  return useQuery({ queryKey: agentKeys.all, queryFn: agentsApi.list });
}

export function useApproveAgent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => agentsApi.approve(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: agentKeys.all }),
  });
}

export function useRevokeAgent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => agentsApi.revoke(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: agentKeys.all }),
  });
}
