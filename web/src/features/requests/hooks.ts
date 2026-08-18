import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { requestsApi } from "./api";

export const requestKeys = { all: ["requests"] as const };

function invalidateAll(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: requestKeys.all });
  qc.invalidateQueries({ queryKey: ["ports"] });
  qc.invalidateQueries({ queryKey: ["tunnels"] });
}

export function useRequests() {
  return useQuery({ queryKey: requestKeys.all, queryFn: requestsApi.list });
}

export function useCreateRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: requestsApi.create,
    onSuccess: () => invalidateAll(qc),
  });
}

export function useApproveRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => requestsApi.approve(id),
    onSuccess: () => invalidateAll(qc),
  });
}

export function useRejectRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => requestsApi.reject(id),
    onSuccess: () => invalidateAll(qc),
  });
}
