import { Card, CardTitle, EmptyState, Table, Td } from "../../../components/ui";
import { useAudits } from "../hooks";
import { shortId, timeAgo } from "../../../lib/format";

export function AuditsPanel() {
  const audits = useAudits();
  const list = audits.data ?? [];
  return (
    <Card>
      <CardTitle>Audit trail (SUPERADMIN)</CardTitle>
      {list.length === 0 ? (
        <EmptyState title="No audit entries" />
      ) : (
        <Table headers={["Time", "Actor", "Type", "Action", "Resource", "Result", "IP"]}>
          {list.map((a) => (
            <tr key={a.id} className="transition-colors hover:bg-hover/50">
              <Td mono>{timeAgo(a.createdAt)}</Td>
              <Td mono>{shortId(a.actor, 13)}</Td>
              <Td>{a.actorType}</Td>
              <Td>{a.action}</Td>
              <Td mono>{a.resourceType ? `${a.resourceType}/${a.resourceId ? shortId(a.resourceId) : ""}` : "—"}</Td>
              <Td>
                <span className={a.result === "SUCCESS" ? "text-ok" : "text-bad"}>{a.result}</span>
              </Td>
              <Td mono>{a.sourceIp ?? "—"}</Td>
            </tr>
          ))}
        </Table>
      )}
    </Card>
  );
}
