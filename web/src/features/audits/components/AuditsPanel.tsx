import { Card, CardTitle, Table, Td, EmptyRow } from "../../../components/ui";
import { useAudits } from "../hooks";

export function AuditsPanel() {
  const audits = useAudits();
  return (
    <Card>
      <CardTitle>Audit trail (SUPERADMIN)</CardTitle>
      <Table headers={["Time", "Actor", "Type", "Action", "Resource", "Result", "IP"]}>
        {(audits.data ?? []).length === 0 && <EmptyRow cols={7} message="No audit entries" />}
        {(audits.data ?? []).map((a) => (
          <tr key={a.id}>
            <Td mono>{new Date(a.createdAt).toLocaleString()}</Td>
            <Td mono>{a.actor.slice(0, 13)}</Td>
            <Td>{a.actorType}</Td>
            <Td>{a.action}</Td>
            <Td mono>{a.resourceType ? `${a.resourceType}/${a.resourceId?.slice(0, 8)}` : "-"}</Td>
            <Td>{a.result}</Td>
            <Td mono>{a.sourceIp ?? "-"}</Td>
          </tr>
        ))}
      </Table>
    </Card>
  );
}
