import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { tunnelsApi } from "./api";

export const tunnelKeys = { all: ["tunnels"] as const };

export function useTunnels() {
  return useQuery({ queryKey: tunnelKeys.all, queryFn: tunnelsApi.list });
}

export function useTunnelUsage(id: string | null) {
  return useQuery({
    queryKey: ["tunnels", id, "usage"],
    queryFn: () => tunnelsApi.usage(id!),
    enabled: id !== null,
  });
}

export function useTunnelUsageHistory(id: string | null, days = 30) {
  return useQuery({
    queryKey: ["tunnels", id, "usage-history", days],
    queryFn: () => tunnelsApi.usageHistory(id!, days),
    enabled: id !== null,
    refetchInterval: 60_000,
  });
}

function invalidate(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: tunnelKeys.all });
}

export function useCreateTunnel() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: tunnelsApi.create, onSuccess: () => invalidate(qc) });
}

export function useCreateHttpTunnel() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: tunnelsApi.createHttp, onSuccess: () => invalidate(qc) });
}

export function useStartTunnel() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (id: string) => tunnelsApi.start(id), onSuccess: () => invalidate(qc) });
}

export function useStopTunnel() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (id: string) => tunnelsApi.stop(id), onSuccess: () => invalidate(qc) });
}

export function useDeleteTunnel() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (id: string) => tunnelsApi.remove(id), onSuccess: () => invalidate(qc) });
}
