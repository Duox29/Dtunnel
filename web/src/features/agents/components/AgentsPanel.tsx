import { useState } from "react";
import {
  Button, Card, CardTitle, ConfirmDialog, CopyButton, EmptyState, StatusBadge,
  Table, Td, useToast,
} from "../../../components/ui";
import { useAgents, useApproveAgent, useRevokeAgent } from "../hooks";
import { shortId, timeAgo } from "../../../lib/format";

const INSTALL_CMD = `duox-agent --server https://your-control-plane --frpc /usr/local/bin/frpc`;

export function AgentsPanel({ isAdmin }: { isAdmin: boolean }) {
  const agents = useAgents();
  const approve = useApproveAgent();
  const revoke = useRevokeAgent();
  const toast = useToast();
  const [confirmRevoke, setConfirmRevoke] = useState<{ id: string } | null>(null);

  const list = agents.data ?? [];

  return (
    <div className="space-y-4">
      <Card>
        <CardTitle>Connect a device</CardTitle>
        <p className="mb-2 text-sm text-ink-dim">
          Run the agent on the machine that hosts your service. It generates an Ed25519
          device key on first run and registers itself for approval.
        </p>
        <div className="flex items-center gap-2 rounded-lg border border-edge-soft bg-panel-2 px-3 py-2">
          <code className="flex-1 overflow-x-auto whitespace-nowrap font-mono text-xs text-ink">{INSTALL_CMD}</code>
          <CopyButton text={INSTALL_CMD} />
        </div>
      </Card>

      <Card>
        <CardTitle>Agents</CardTitle>
        {list.length === 0 ? (
          <EmptyState title="No agents registered" body="Run the agent command above on a device to register it." />
        ) : (
          <Table headers={["ID", "Platform", "Version", "Status", "Last seen", ""]}>
            {list.map((a) => (
              <tr key={a.id} className="transition-colors hover:bg-hover/50">
                <Td>
                  <span className="flex items-center gap-1.5">
                    <span className="font-mono text-xs">{shortId(a.id)}</span>
                    <CopyButton text={a.id} label="" />
                  </span>
                </Td>
                <Td>{a.platform}</Td>
                <Td>{a.agentVersion ?? "—"}</Td>
                <Td><StatusBadge status={a.status} /></Td>
                <Td>{timeAgo(a.lastSeenAt)}</Td>
                <Td>
                  {isAdmin && (
                    <span className="flex justify-end gap-1">
                      {a.status === "PENDING" && (
                        <Button
                          disabled={approve.isPending}
                          onClick={() =>
                            approve.mutate(a.id, {
                              onSuccess: () => toast("success", `Agent ${shortId(a.id)} approved`),
                              onError: (e) => toast("error", e instanceof Error ? e.message : "Approve failed"),
                            })
                          }
                        >
                          Approve
                        </Button>
                      )}
                      {a.status !== "REVOKED" && (
                        <Button variant="danger" disabled={revoke.isPending} onClick={() => setConfirmRevoke({ id: a.id })}>
                          Revoke
                        </Button>
                      )}
                    </span>
                  )}
                </Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      <ConfirmDialog
        open={confirmRevoke !== null}
        title="Revoke agent?"
        body="The device loses access immediately and must re-register to reconnect. This cannot be undone."
        confirmLabel="Revoke"
        danger
        busy={revoke.isPending}
        onCancel={() => setConfirmRevoke(null)}
        onConfirm={() => {
          if (!confirmRevoke) return;
          revoke.mutate(confirmRevoke.id, {
            onSuccess: () => { toast("success", `Agent ${shortId(confirmRevoke.id)} revoked`); setConfirmRevoke(null); },
            onError: (e) => { toast("error", e instanceof Error ? e.message : "Revoke failed"); setConfirmRevoke(null); },
          });
        }}
      />
    </div>
  );
}
