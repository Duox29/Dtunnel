import { useState } from "react";
import {
  Button, Card, CardTitle, ConfirmDialog, CopyButton, EmptyState, Input, Select,
  StatusBadge, Table, Td, useToast,
} from "../../../components/ui";
import { useAgents } from "../../agents/hooks";
import { useNodes } from "../../nodes/hooks";
import { usePorts } from "../../ports/hooks";
import { useTunnels, useCreateTunnel, useCreateHttpTunnel, useStartTunnel, useStopTunnel, useDeleteTunnel, useTunnelUsage } from "../hooks";
import { UsageChart } from "./UsageChart";
import { formatBytes, shortId } from "../../../lib/format";

function UsageCell({ tunnelId }: { tunnelId: string }) {
  const usage = useTunnelUsage(tunnelId);
  if (!usage.data) return <span className="text-ink-faint">—</span>;
  return (
    <span className="font-mono text-xs tabular-nums">
      <span className="text-accent">↓{formatBytes(usage.data.bytesIn)}</span>{" "}
      <span className="text-ok">↑{formatBytes(usage.data.bytesOut)}</span>
    </span>
  );
}

// detail.md Milestone 4.2 (v0.1 §28): intent-based "Create Tunnel" form.
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
  const nodes = useNodes();
  const createTunnel = useCreateTunnel();
  const createHttp = useCreateHttpTunnel();
  const start = useStartTunnel();
  const stop = useStopTunnel();
  const remove = useDeleteTunnel();
  const toast = useToast();

  // detail.md §3.6: two tunnel kinds — dedicated-port (tcp/udp) and
  // domain-routed HTTP over the node's shared vhost port.
  const [mode, setMode] = useState<"port" | "domain">("port");
  const [intent, setIntent] = useState<string>("ssh");
  const [alloc, setAlloc] = useState("");
  const [agentId, setAgentId] = useState("");
  const [name, setName] = useState("");
  const [host, setHost] = useState("127.0.0.1");
  const [port, setPort] = useState("22");
  const [httpNode, setHttpNode] = useState("");
  const [domain, setDomain] = useState("");
  const [expanded, setExpanded] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ kind: "stop" | "delete"; id: string; name: string } | null>(null);

  const selectedIntent = INTENTS.find((i) => i.id === intent) ?? INTENTS[0];

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
    (createHttp.error instanceof Error && createHttp.error.message) || "";

  const usable = (ports.data ?? []).filter((p) => p.allocationId);
  const allocPort = (allocId: string) => usable.find((p) => p.allocationId === allocId);
  const nodeFor = (nodeId: string) => nodes.data?.find((n) => n.id === nodeId);
  const httpNodes = (nodes.data ?? []).filter((n) => n.vhostHttpPort);

  const canCreate =
    mode === "port"
      ? !createTunnel.isPending && !!alloc && !!agentId && !!name && !!port && Number(port) > 0
      : !createHttp.isPending && !!httpNode && !!agentId && !!name && !!domain && !!port && Number(port) > 0;

  const list = tunnels.data ?? [];

  return (
    <Card>
      <CardTitle>Tunnels</CardTitle>

      <div className="mb-4 rounded-lg border border-edge-soft bg-panel-2 p-3">
        <div className="mb-2 flex items-center justify-between">
          <p className="text-xs font-semibold uppercase tracking-wider text-ink-dim">
            New tunnel — what do you want to expose?
          </p>
          <div className="flex rounded-md border border-edge-soft text-xs">
            {(["port", "domain"] as const).map((m) => (
              <button
                key={m}
                onClick={() => setMode(m)}
                className={`px-2.5 py-1 transition-colors ${
                  mode === m ? "bg-accent-soft text-accent" : "text-ink-dim hover:text-ink"
                } ${m === "port" ? "rounded-l-md" : "rounded-r-md"}`}
              >
                {m === "port" ? "Port (TCP/UDP)" : "Domain (HTTP)"}
              </button>
            ))}
          </div>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          {mode === "port" ? (
            <>
              <label className="flex flex-col gap-1 text-xs text-ink-dim">
                Service
                <Select value={intent} onChange={chooseIntent}>
                  {INTENTS.map((i) => (
                    <option key={i.id} value={i.id}>{i.label}</option>
                  ))}
                </Select>
              </label>
              <label className="flex flex-col gap-1 text-xs text-ink-dim">
                Allocation (public port)
                <Select value={alloc} onChange={setAlloc}>
                  <option value="">choose…</option>
                  {usable.map((p) => (
                    <option key={p.id} value={p.allocationId!}>:{p.portNumber} ({p.protocol})</option>
                  ))}
                </Select>
              </label>
            </>
          ) : (
            <>
              <label className="flex flex-col gap-1 text-xs text-ink-dim">
                Node (HTTP edge)
                <Select value={httpNode} onChange={setHttpNode}>
                  <option value="">choose…</option>
                  {httpNodes.map((n) => (
                    <option key={n.id} value={n.id}>{n.code} (:{n.vhostHttpPort})</option>
                  ))}
                </Select>
              </label>
              <label className="flex flex-col gap-1 text-xs text-ink-dim">
                Public domain
                <Input placeholder="app.example.com" value={domain} onChange={setDomain} className="w-44" />
              </label>
            </>
          )}
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            Agent (your device)
            <Select value={agentId} onChange={setAgentId}>
              <option value="">choose…</option>
              {(agents.data ?? []).map((a) => (
                <option key={a.id} value={a.id}>{shortId(a.id)} ({a.platform})</option>
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
            <Input placeholder={mode === "port" ? "22" : "8000"} value={port} onChange={setPort} className="w-24" />
          </label>
          <Button
            disabled={!canCreate}
            onClick={() => {
              const done = {
                onSuccess: () => { toast("success", `Tunnel "${name}" created`); setName(""); setDomain(""); },
                onError: (e: unknown) => toast("error", e instanceof Error ? e.message : "Create failed"),
              };
              if (mode === "port") {
                createTunnel.mutate(
                  { allocationId: alloc, agentId, name, targetHost: host, targetPort: Number(port) }, done);
              } else {
                createHttp.mutate(
                  { nodeId: httpNode, agentId, name, domain, targetHost: host, targetPort: Number(port) }, done);
              }
            }}
          >
            Create tunnel
          </Button>
        </div>
        {mode === "port" && alloc && agentId && port && (
          <p className="mt-2 text-xs text-ink-faint">
            Will expose <span className="font-mono text-ink-dim">{host}:{port}</span> ({selectedIntent.label})
            {" "}on public port <span className="font-mono text-ink-dim">{allocPort(alloc)?.portNumber ?? "?"}</span>
          </p>
        )}
        {mode === "domain" && domain && agentId && port && (
          <p className="mt-2 text-xs text-ink-faint">
            Will expose <span className="font-mono text-ink-dim">{host}:{port}</span> as{" "}
            <span className="font-mono text-ink-dim">http://{domain.toLowerCase()}</span> via the node's HTTP edge
          </p>
        )}
      </div>

      {error && <p className="mb-2 text-sm text-bad">{error}</p>}

      {list.length === 0 ? (
        <EmptyState
          title="No tunnels yet"
          body="Request a port, register an agent, then create your first tunnel above."
        />
      ) : (
        <Table headers={["", "Name", "Target", "Public", "Usage", "Status", ""]}>
          {list.map((t) => {
            const p = t.allocationId ? usable.find((x) => x.allocationId === t.allocationId) : undefined;
            const node = t.type === "HTTP" ? nodeFor(t.nodeId ?? "") : p ? nodeFor(p.nodeId) : undefined;
            const endpoint =
              t.type === "HTTP" && t.domain
                ? `http://${t.domain}`
                : p && node ? `${node.publicAddress}:${p.portNumber}` : null;
            const isOpen = expanded === t.id;
            return (
              <FragmentRow key={t.id}>
                <tr className="transition-colors hover:bg-hover/50">
                  <Td>
                    <button
                      className="rounded p-0.5 text-ink-faint transition-transform hover:text-ink"
                      style={{ transform: isOpen ? "rotate(90deg)" : undefined }}
                      onClick={() => setExpanded(isOpen ? null : t.id)}
                      title={isOpen ? "Collapse" : "Traffic details"}
                    >
                      <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
                        <path d="m6 4 4 4-4 4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    </button>
                  </Td>
                  <Td>
                    <span className="font-medium">{t.name}</span>
                    <span className={`ml-2 rounded px-1.5 py-0.5 text-[10px] ${
                      t.type === "HTTP" ? "bg-accent-soft text-accent" : "bg-edge-soft text-ink-dim"
                    }`}>
                      {t.type === "HTTP" ? "HTTP" : "TCP/UDP"}
                    </span>
                    {t.bandwidthLimitMbps ? (
                      <span className="ml-2 rounded bg-edge-soft px-1.5 py-0.5 text-[10px] text-ink-dim">
                        {t.bandwidthLimitMbps} Mbps cap
                      </span>
                    ) : null}
                  </Td>
                  <Td mono>{t.targetHost}:{t.targetPort}</Td>
                  <Td>
                    {endpoint ? (
                      <span className="flex items-center gap-1.5">
                        <span className="font-mono text-xs">{endpoint}</span>
                        <CopyButton text={endpoint} label="" />
                      </span>
                    ) : (
                      <span className="text-ink-faint">—</span>
                    )}
                  </Td>
                  <Td><UsageCell tunnelId={t.id} /></Td>
                  <Td><StatusBadge status={t.status} /></Td>
                  <Td>
                    <span className="flex justify-end gap-1">
                      {["CONFIGURED", "STOPPED", "ERROR"].includes(t.status) && (
                        <Button
                          disabled={start.isPending}
                          onClick={() =>
                            start.mutate(t.id, {
                              onSuccess: () => toast("success", `Starting "${t.name}"…`),
                              onError: (e) => toast("error", e instanceof Error ? e.message : "Start failed"),
                            })
                          }
                        >
                          Start
                        </Button>
                      )}
                      {["ACTIVE", "STARTING"].includes(t.status) && (
                        <Button variant="ghost" disabled={stop.isPending} onClick={() => setConfirm({ kind: "stop", id: t.id, name: t.name })}>
                          Stop
                        </Button>
                      )}
                      {["STOPPED", "EXPIRED", "ERROR"].includes(t.status) && (
                        <Button variant="danger" disabled={remove.isPending} onClick={() => setConfirm({ kind: "delete", id: t.id, name: t.name })}>
                          Delete
                        </Button>
                      )}
                    </span>
                  </Td>
                </tr>
                {isOpen && (
                  <tr>
                    <td colSpan={7} className="border-b border-edge-soft bg-panel-2/60 px-4 py-3">
                      <p className="mb-1 text-xs font-medium uppercase tracking-wider text-ink-dim">
                        Traffic — last 30 days
                      </p>
                      <UsageChart tunnelId={t.id} />
                    </td>
                  </tr>
                )}
              </FragmentRow>
            );
          })}
        </Table>
      )}

      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.kind === "delete" ? "Delete tunnel?" : "Stop tunnel?"}
        body={
          confirm?.kind === "delete"
            ? `"${confirm?.name}" and its configuration will be removed. The port allocation is kept.`
            : `"${confirm?.name}" will stop serving traffic. You can start it again anytime.`
        }
        confirmLabel={confirm?.kind === "delete" ? "Delete" : "Stop"}
        danger={confirm?.kind === "delete"}
        busy={stop.isPending || remove.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (!confirm) return;
          const m = confirm.kind === "delete" ? remove : stop;
          m.mutate(confirm.id, {
            onSuccess: () => {
              toast("success", `${confirm.kind === "delete" ? "Deleted" : "Stopped"} "${confirm.name}"`);
              setConfirm(null);
            },
            onError: (e) => {
              toast("error", e instanceof Error ? e.message : "Action failed");
              setConfirm(null);
            },
          });
        }}
      />
    </Card>
  );
}

/** Table rows can be siblings (row + optional expanded row). */
function FragmentRow({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
