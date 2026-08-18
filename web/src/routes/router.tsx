import { createRootRoute, createRoute, createRouter, redirect } from "@tanstack/react-router";
import { authApi } from "../features/auth/api";
import { AuthPage } from "../features/auth/components/AuthPage";
import { DashboardLayout } from "./DashboardLayout";
import { OverviewPage, TunnelsPage, RequestsPage, AgentsPage, AdminPage } from "./pages";

const rootRoute = createRootRoute();

const layoutRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "layout",
  component: DashboardLayout,
  // Route guard: every page requires an authenticated session (detail.md §8).
  beforeLoad: async () => {
    try {
      await authApi.me();
    } catch {
      throw redirect({ to: "/login" });
    }
  },
});

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  component: AuthPage,
  // Already signed in? Go straight to the dashboard.
  beforeLoad: async () => {
    try {
      await authApi.me();
    } catch {
      return; // not authenticated → render the login page
    }
    throw redirect({ to: "/" });
  },
});

const indexRoute = createRoute({ getParentRoute: () => layoutRoute, path: "/", component: OverviewPage });
const tunnelsRoute = createRoute({ getParentRoute: () => layoutRoute, path: "/tunnels", component: TunnelsPage });
const requestsRoute = createRoute({ getParentRoute: () => layoutRoute, path: "/requests", component: RequestsPage });
const agentsRoute = createRoute({ getParentRoute: () => layoutRoute, path: "/agents", component: AgentsPage });
const adminRoute = createRoute({ getParentRoute: () => layoutRoute, path: "/admin", component: AdminPage });

const routeTree = rootRoute.addChildren([
  loginRoute,
  layoutRoute.addChildren([indexRoute, tunnelsRoute, requestsRoute, agentsRoute, adminRoute]),
]);

export const router = createRouter({ routeTree });

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
