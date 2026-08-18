import { Button, Card, CardTitle, StatusBadge, Table, Td, EmptyRow } from "../../../components/ui";
import { useAgents, useApproveAgent, useRevokeAgent } from "../hooks";

export function AgentsPanel({ isAdmin }: { isAdmin: boolean }) {
  const agents = useAgents();
  const approve = useApproveAgent();
  const revoke = useRevokeAgent();

  return (
    <Card>
      <CardTitle>Agents</CardTitle>
      <Table headers={["ID", "Platform", "Version", "Status", "Last seen", ""]}>
        {(agents.data ?? []).length === 0 && <EmptyRow cols={6} message="No agents registered" />}
        {(agents.data ?? []).map((a) => (
          <tr key={a.id}>
            <Td mono>{a.id.slice(0, 8)}</Td>
            <Td>{a.platform}</Td>
            <Td>{a.agentVersion ?? "-"}</Td>
            <Td><StatusBadge status={a.status} /></Td>
            <Td>{a.lastSeenAt ? new Date(a.lastSeenAt).toLocaleTimeString() : "-"}</Td>
            <Td>
              {isAdmin && (
                <span className="flex gap-1">
                  {a.status === "PENDING" && (
                    <Button disabled={approve.isPending} onClick={() => approve.mutate(a.id)}>Approve</Button>
                  )}
                  {a.status !== "REVOKED" && (
                    <Button variant="danger" disabled={revoke.isPending} onClick={() => revoke.mutate(a.id)}>Revoke</Button>
                  )}
                </span>
              )}
            </Td>
          </tr>
        ))}
      </Table>
    </Card>
  );
}
