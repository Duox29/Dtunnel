import { Card, CardTitle, StatusBadge, Table, Td, EmptyRow } from "../../../components/ui";
import { useNodes } from "../../nodes/hooks";
import { usePorts } from "../hooks";

export function PortsPanel() {
  const ports = usePorts();
  const nodes = useNodes();
  const nodeCode = (id: string) => nodes.data?.find((n) => n.id === id)?.code ?? id.slice(0, 8);

  return (
    <Card>
      <CardTitle>My ports</CardTitle>
      <Table headers={["Node", "Proto", "Port", "Status", "Expires", "Allocation"]}>
        {(ports.data ?? []).length === 0 && <EmptyRow cols={6} message="No allocated ports" />}
        {(ports.data ?? []).map((p) => (
          <tr key={p.id}>
            <Td>{nodeCode(p.nodeId)}</Td>
            <Td>{p.protocol}</Td>
            <Td mono>{p.portNumber}</Td>
            <Td><StatusBadge status={p.status} /></Td>
            <Td>{p.expiresAt ? new Date(p.expiresAt).toLocaleDateString() : "-"}</Td>
            <Td mono>{p.allocationId?.slice(0, 8) ?? "-"}</Td>
          </tr>
        ))}
      </Table>
    </Card>
  );
}
