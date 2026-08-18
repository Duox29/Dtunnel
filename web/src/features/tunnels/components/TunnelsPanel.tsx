import { useState } from "react";
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

export function TunnelsPanel() {
  const tunnels = useTunnels();
  const ports = usePorts();
  const agents = useAgents();
  const createTunnel = useCreateTunnel();
  const start = useStartTunnel();
  const stop = useStopTunnel();
  const remove = useDeleteTunnel();

  const [alloc, setAlloc] = useState("");
  const [agentId, setAgentId] = useState("");
  const [name, setName] = useState("");
  const [host, setHost] = useState("127.0.0.1");
  const [port, setPort] = useState("");

  const error =
    (createTunnel.error instanceof Error && createTunnel.error.message) ||
    (start.error instanceof Error && start.error.message) ||
    (stop.error instanceof Error && stop.error.message) ||
    (remove.error instanceof Error && remove.error.message) ||
    "";

  const usable = (ports.data ?? []).filter((p) => p.allocationId);

  return (
    <Card>
      <CardTitle>Tunnels</CardTitle>
      <div className="mb-3 flex flex-wrap items-end gap-2">
        <Select value={alloc} onChange={setAlloc}>
          <option value="">allocation…</option>
          {usable.map((p) => (
            <option key={p.id} value={p.allocationId!}>:{p.portNumber} ({p.protocol})</option>
          ))}
        </Select>
        <Select value={agentId} onChange={setAgentId}>
          <option value="">agent…</option>
          {(agents.data ?? []).map((a) => (
            <option key={a.id} value={a.id}>{a.id.slice(0, 8)} ({a.platform})</option>
          ))}
        </Select>
        <Input placeholder="name" value={name} onChange={setName} className="w-32" />
        <Input placeholder="target host" value={host} onChange={setHost} className="w-36" />
        <Input placeholder="target port" value={port} onChange={setPort} className="w-28" />
        <Button
          disabled={createTunnel.isPending || !alloc || !agentId || !name || !port}
          onClick={() =>
            createTunnel.mutate({ allocationId: alloc, agentId, name, targetHost: host, targetPort: Number(port) })
          }
        >
          Create tunnel
        </Button>
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
