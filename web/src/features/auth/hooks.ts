import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { authApi } from "./api";

export const authKeys = { me: ["auth", "me"] as const };

export function useMe() {
  return useQuery({
    queryKey: authKeys.me,
    queryFn: () => authApi.me().catch(() => null),
    retry: false,
    refetchInterval: false,
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { email: string; password: string }) => authApi.login(vars.email, vars.password),
    onSuccess: () => qc.invalidateQueries({ queryKey: authKeys.me }),
  });
}

export function useRegister() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { email: string; password: string }) => authApi.register(vars.email, vars.password),
    onSuccess: () => qc.invalidateQueries({ queryKey: authKeys.me }),
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => authApi.logout(),
    onSuccess: () => {
      qc.clear();
      qc.setQueryData(authKeys.me, null);
    },
  });
}
