import { useQuery } from "@tanstack/react-query";
import { auditsApi } from "./api";

export function useAudits(page = 0) {
  return useQuery({
    queryKey: ["audits", page],
    queryFn: () => auditsApi.list(page),
  });
}
