import { useMemo, useState } from "react";
import { Button, Card, CardTitle, Input, Select, StatusBadge, Table, Td, EmptyRow } from "../../../components/ui";
import { useAgents } from "../../agents/hooks";
import { usePorts } from "../../ports/hooks";
import { useTunnels, useCreateTunnel, useStartTunnel, useStopTunnel, useDeleteTunnel, useTunnelUsage } from "../hooks";

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(1)} KB`;
  if (n < 1024 ** 3) return `${(n / 1024 ** 2).toFixed(1)} MB`;
  return `${(n / 1024 ** 3).toFixed(2)} GB`;
}

function UsageCell({ tunnelId }: { tunnelId: string }) {
  const usage = useTunnelUsage(tunnelId);
  if (!usage.data) return <span className="text-ink-dim">-</span>;
  return (
    <span className="font-mono text-xs">
      ↓{formatBytes(usage.data.bytesIn)} ↑{formatBytes(usage.data.bytesOut)}
    </span>
  );
}

// detail.md Milestone 4.2 (v0.1 §28): intent-based "Create Tunnel" form.
// The user states WHAT they want to expose; the form derives the technical
// details (target port, suggested name) instead of asking for raw numbers.
const INTENTS = [
  { id: "ssh", label: "SSH (remote shell)", port: 22 },
  { id: "http", label: "HTTP (web service)", port: 80 },
  { id: "https", label: "HTTPS (secure web)", port: 443 },
  { id: "rdp", label: "RDP (Windows desktop)", port: 3389 },
  { id: "mysql", label: "MySQL", port: 3306 },
  { id: "postgres", label: "PostgreSQL", port: 5432 },
  { id: "custom", label: "Custom TCP", port: 0 },
] as const;

export function TunnelsPanel() {
  const tunnels = useTunnels();
  const ports = usePorts();
  const agents = useAgents();
  const createTunnel = useCreateTunnel();
  const start = useStartTunnel();
  const stop = useStopTunnel();
  const remove = useDeleteTunnel();

  const [intent, setIntent] = useState<string>("ssh");
  const [alloc, setAlloc] = useState("");
  const [agentId, setAgentId] = useState("");
  const [name, setName] = useState("");
  const [host, setHost] = useState("127.0.0.1");
  const [port, setPort] = useState("22");

  const selectedIntent = INTENTS.find((i) => i.id === intent) ?? INTENTS[0];

  // Choosing an intent pre-fills the target port and suggests a name.
  function chooseIntent(id: string) {
    setIntent(id);
    const found = INTENTS.find((i) => i.id === id);
    if (found && found.port > 0) {
      setPort(String(found.port));
      if (!name || INTENTS.some((i) => name.startsWith(i.id))) setName(found.id);
    }
  }

  const error =
    (createTunnel.error instanceof Error && createTunnel.error.message) ||
    (start.error instanceof Error && start.error.message) ||
    (stop.error instanceof Error && stop.error.message) ||
    (remove.error instanceof Error && remove.error.message) ||
    "";

  const usable = (ports.data ?? []).filter((p) => p.allocationId);

  const canCreate =
    !createTunnel.isPending && !!alloc && !!agentId && !!name && !!port && Number(port) > 0;

  const summary = useMemo(() => {
    if (!alloc || !agentId || !port) return "";
    const p = usable.find((x) => x.allocationId === alloc);
    return `Expose ${host}:${port} (${selectedIntent.label}) on public port ${p?.portNumber ?? "?"}`;
  }, [alloc, agentId, host, port, selectedIntent, usable]);

  return (
    <Card>
      <CardTitle>Tunnels</CardTitle>

      <div className="mb-3 rounded-lg border border-line bg-panel p-3">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-dim">
          Create tunnel — what do you want to expose?
        </p>
        <div className="flex flex-wrap items-end gap-2">
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            Service
            <Select value={intent} onChange={chooseIntent}>
              {INTENTS.map((i) => (
                <option key={i.id} value={i.id}>{i.label}</option>
              ))}
            </Select>
          </label>
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            Allocation (node + public port)
            <Select value={alloc} onChange={setAlloc}>
              <option value="">choose…</option>
              {usable.map((p) => (
                <option key={p.id} value={p.allocationId!}>:{p.portNumber} ({p.protocol})</option>
              ))}
            </Select>
          </label>
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            Agent (your device)
            <Select value={agentId} onChange={setAgentId}>
              <option value="">choose…</option>
              {(agents.data ?? []).map((a) => (
                <option key={a.id} value={a.id}>{a.id.slice(0, 8)} ({a.platform})</option>
              ))}
            </Select>
          </label>
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            Name
            <Input placeholder="name" value={name} onChange={setName} className="w-32" />
          </label>
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            Target host
            <Input placeholder="127.0.0.1" value={host} onChange={setHost} className="w-36" />
          </label>
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            Target port
            <Input placeholder="22" value={port} onChange={setPort} className="w-24" />
          </label>
          <Button
            disabled={!canCreate}
            onClick={() =>
              createTunnel.mutate({ allocationId: alloc, agentId, name, targetHost: host, targetPort: Number(port) })
            }
          >
            Create tunnel
          </Button>
        </div>
        {summary && <p className="mt-2 text-xs text-ink-dim">{summary}</p>}
      </div>

      {error && <p className="mb-2 text-sm text-bad">{error}</p>}
      <Table headers={["Name", "Target", "Usage", "Status", ""]}>
        {(tunnels.data ?? []).length === 0 && <EmptyRow cols={5} message="No tunnels yet" />}
        {(tunnels.data ?? []).map((t) => (
          <tr key={t.id}>
            <Td>{t.name}</Td>
            <Td mono>{t.targetHost}:{t.targetPort}</Td>
            <Td><UsageCell tunnelId={t.id} /></Td>
            <Td><StatusBadge status={t.status} /></Td>
            <Td>
              <span className="flex gap-1">
                {["CONFIGURED", "STOPPED", "ERROR"].includes(t.status) && (
                  <Button disabled={start.isPending} onClick={() => start.mutate(t.id)}>Start</Button>
                )}
                {["ACTIVE", "STARTING"].includes(t.status) && (
                  <Button disabled={stop.isPending} onClick={() => stop.mutate(t.id)}>Stop</Button>
                )}
                {["STOPPED", "EXPIRED", "ERROR"].includes(t.status) && (
                  <Button variant="danger" disabled={remove.isPending} onClick={() => remove.mutate(t.id)}>Delete</Button>
                )}
              </span>
            </Td>
          </tr>
        ))}
      </Table>
    </Card>
  );
}
