import { QueryClient } from "@tanstack/react-query";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchInterval: 5_000, // control-plane state moves; keep dashboards fresh
      retry: 1,
      staleTime: 2_000,
    },
  },
});
