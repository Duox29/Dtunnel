import { NodesPanel } from "../features/nodes/components/NodesPanel";
import { RequestsPanel } from "../features/requests/components/RequestsPanel";
import { PortsPanel } from "../features/ports/components/PortsPanel";
import { AgentsPanel } from "../features/agents/components/AgentsPanel";
import { TunnelsPanel } from "../features/tunnels/components/TunnelsPanel";
import { AuditsPanel } from "../features/audits/components/AuditsPanel";
import { useMe } from "../features/auth/hooks";

function useIsAdmin() {
  const me = useMe();
  return me.data?.role === "SUPERADMIN";
}

export function OverviewPage() {
  const isAdmin = useIsAdmin();
  return (
    <div className="space-y-4">
      <TunnelsPanel />
      <PortsPanel />
      <RequestsPanel isAdmin={isAdmin} />
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
