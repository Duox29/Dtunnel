import { Link, Outlet, useRouter } from "@tanstack/react-router";
import { useMe, useLogout } from "../features/auth/hooks";

const icon = (d: string) => (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden>
    <path d={d} stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const navItems = [
  { to: "/", label: "Overview", icon: icon("M2 8.5 8 3l6 5.5M3.5 7.5V13h9V7.5") },
  { to: "/tunnels", label: "Tunnels", icon: icon("M2 5.5h12M2 10.5h12M5.5 3v5M10.5 8v5") },
  { to: "/requests", label: "Requests", icon: icon("M4 2.5h8a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1ZM5.5 5.5h5M5.5 8h5M5.5 10.5h3") },
  { to: "/agents", label: "Agents", icon: icon("M8 8.5a2.25 2.25 0 1 0 0-4.5 2.25 2.25 0 0 0 0 4.5ZM3.5 13.5a4.5 4.5 0 0 1 9 0") },
  { to: "/admin", label: "Admin", adminOnly: true, icon: icon("M8 2 3 4v3.5c0 3 2 5.5 5 6.5 3-1 5-3.5 5-6.5V4L8 2Z") },
];

/**
 * Market-style app shell: fixed left sidebar (ngrok / Cloudflare Zero Trust
 * pattern) + content column. Sidebar carries brand, nav, and the session user.
 */
export function DashboardLayout() {
  const me = useMe();
  const logout = useLogout();
  const router = useRouter();

  if (!me.data) return null;
  const isAdmin = me.data.role === "SUPERADMIN";

  return (
    <div className="flex min-h-screen">
      <aside className="fixed inset-y-0 left-0 z-40 flex w-56 flex-col border-r border-edge bg-sidebar">
        <div className="flex items-center gap-2 px-4 py-4">
          <span className="flex h-7 w-7 items-center justify-center rounded-md bg-accent-soft text-accent">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden>
              <path d="M3 11V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v6M3 11h10M5.5 11v2M10.5 11v2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          </span>
          <span className="text-base font-semibold tracking-tight">dtunnel</span>
        </div>

        <nav className="mt-2 flex flex-1 flex-col gap-0.5 px-2">
          {navItems
            .filter((item) => !item.adminOnly || isAdmin)
            .map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className="flex items-center gap-2.5 rounded-md px-3 py-2 text-sm text-ink-dim transition-colors hover:bg-hover hover:text-ink"
                activeProps={{ className: "flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium bg-accent-soft text-accent" }}
                activeOptions={{ exact: item.to === "/" }}
              >
                {item.icon}
                {item.label}
              </Link>
            ))}
        </nav>

        <div className="border-t border-edge px-3 py-3">
          <div className="flex items-center gap-2.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-edge-soft text-xs font-semibold uppercase text-ink-dim">
              {me.data.email.slice(0, 2)}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-medium text-ink">{me.data.email}</p>
              <p className="text-[11px] text-ink-faint">{me.data.role === "SUPERADMIN" ? "Super admin" : "User"} · {me.data.plan}</p>
            </div>
            <button
              title="Sign out"
              className="rounded-md p-1.5 text-ink-faint transition-colors hover:bg-hover hover:text-ink"
              onClick={() => logout.mutate(undefined, { onSuccess: () => router.invalidate() })}
            >
              <svg width="15" height="15" viewBox="0 0 16 16" fill="none" aria-hidden>
                <path d="M6 2.5H3.5a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1H6M10.5 5 13.5 8l-3 3M13.2 8H6.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
          </div>
        </div>
      </aside>

      <main className="ml-56 flex-1 px-6 py-6">
        <div className="mx-auto max-w-6xl">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
