import { NodesPanel } from "../features/nodes/components/NodesPanel";
import { RequestsPanel } from "../features/requests/components/RequestsPanel";
import { PortsPanel } from "../features/ports/components/PortsPanel";
import { AgentsPanel } from "../features/agents/components/AgentsPanel";
import { TunnelsPanel } from "../features/tunnels/components/TunnelsPanel";
import { AuditsPanel } from "../features/audits/components/AuditsPanel";
import { StatCard } from "../components/ui";
import { useMe } from "../features/auth/hooks";
import { useTunnels } from "../features/tunnels/hooks";
import { useAgents } from "../features/agents/hooks";
import { useNodes } from "../features/nodes/hooks";
import { usePorts } from "../features/ports/hooks";
import { useRequests } from "../features/requests/hooks";

function useIsAdmin() {
  const me = useMe();
  return me.data?.role === "SUPERADMIN";
}

/** KPI strip: the at-a-glance row market dashboards lead with. */
function OverviewStats() {
  const tunnels = useTunnels();
  const agents = useAgents();
  const nodes = useNodes();
  const ports = usePorts();
  const requests = useRequests();

  const t = tunnels.data ?? [];
  const active = t.filter((x) => x.status === "ACTIVE").length;
  const errored = t.filter((x) => x.status === "ERROR").length;
  const onlineAgents = (agents.data ?? []).filter((a) => a.status === "ONLINE").length;
  const onlineNodes = (nodes.data ?? []).filter((n) => n.status === "ONLINE").length;
  const myPorts = (ports.data ?? []).filter((p) => p.allocationId).length;
  const pendingReqs = (requests.data ?? []).filter((r) => r.status === "PENDING").length;

  return (
    <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
      <StatCard label="Tunnels" value={t.length} hint={`${active} active`} tone={t.length ? "accent" : "default"} />
      <StatCard label="Active" value={active} tone={active ? "ok" : "default"} hint="serving traffic" />
      <StatCard label="Errors" value={errored} tone={errored ? "bad" : "ok"} hint={errored ? "needs attention" : "all healthy"} />
      <StatCard label="Agents" value={(agents.data ?? []).length} hint={`${onlineAgents} online`} />
      <StatCard label="Nodes" value={(nodes.data ?? []).length} hint={`${onlineNodes} online`} />
      <StatCard label="My ports" value={myPorts} hint={pendingReqs ? `${pendingReqs} pending request` : "allocated"} />
    </div>
  );
}

export function OverviewPage() {
  const isAdmin = useIsAdmin();
  return (
    <div className="space-y-4">
      <OverviewStats />
      <TunnelsPanel />
      <div className="grid gap-4 xl:grid-cols-2">
        <PortsPanel />
        <RequestsPanel isAdmin={isAdmin} />
      </div>
    </div>
  );
}

export function TunnelsPage() {
  return (
    <div className="space-y-4">
      <TunnelsPanel />
      <PortsPanel />
    </div>
  );
}

export function RequestsPage() {
  const isAdmin = useIsAdmin();
  return <RequestsPanel isAdmin={isAdmin} />;
}

export function AgentsPage() {
  const isAdmin = useIsAdmin();
  return <AgentsPanel isAdmin={isAdmin} />;
}

export function AdminPage() {
  const isAdmin = useIsAdmin();
  if (!isAdmin) {
    return <p className="text-sm text-ink-dim">SUPERADMIN access required.</p>;
  }
  return (
    <div className="space-y-4">
      <NodesPanel />
      <AgentsPanel isAdmin />
      <AuditsPanel />
    </div>
  );
}
