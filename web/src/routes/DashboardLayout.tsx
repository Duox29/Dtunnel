import { Link, Outlet, useRouter } from "@tanstack/react-router";
import { useMe, useLogout } from "../features/auth/hooks";
import { Button } from "../components/ui";

const navItems = [
  { to: "/", label: "Overview" },
  { to: "/tunnels", label: "Tunnels" },
  { to: "/requests", label: "Requests" },
  { to: "/agents", label: "Agents" },
  { to: "/admin", label: "Admin", adminOnly: true },
];

export function DashboardLayout() {
  const me = useMe();
  const logout = useLogout();
  const router = useRouter();

  if (!me.data) return null;
  const isAdmin = me.data.role === "SUPERADMIN";

  return (
    <div className="mx-auto max-w-6xl p-4">
      <header className="mb-4 flex items-center gap-4 rounded-xl border border-edge bg-panel px-4 py-3">
        <h1 className="text-lg font-semibold">dtunnel</h1>
        <nav className="flex flex-1 gap-1">
          {navItems
            .filter((item) => !item.adminOnly || isAdmin)
            .map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className="rounded-md px-3 py-1.5 text-sm text-ink-dim hover:bg-edge-soft hover:text-ink"
                activeProps={{ className: "rounded-md px-3 py-1.5 text-sm bg-edge-soft text-ink font-medium" }}
              >
                {item.label}
              </Link>
            ))}
        </nav>
        <span className="text-sm text-ink-dim">{me.data.email} · {me.data.role}</span>
        <Button
          variant="ghost"
          onClick={() => logout.mutate(undefined, { onSuccess: () => router.invalidate() })}
        >
          Sign out
        </Button>
      </header>
      <Outlet />
    </div>
  );
}
