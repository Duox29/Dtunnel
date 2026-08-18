import { Card, CardTitle, EmptyState, StatusBadge, Table, Td } from "../../../components/ui";
import { useNodes } from "../../nodes/hooks";
import { usePorts } from "../hooks";
import { shortId, timeAgo } from "../../../lib/format";

export function PortsPanel() {
  const ports = usePorts();
  const nodes = useNodes();
  const nodeCode = (id: string) => nodes.data?.find((n) => n.id === id)?.code ?? shortId(id);
  const list = ports.data ?? [];

  return (
    <Card>
      <CardTitle>My ports</CardTitle>
      {list.length === 0 ? (
        <EmptyState title="No allocated ports" body="Submit a resource request to get a public port on a node." />
      ) : (
        <Table headers={["Node", "Proto", "Port", "Status", "Expires", "Allocation"]}>
          {list.map((p) => (
            <tr key={p.id} className="transition-colors hover:bg-hover/50">
              <Td>{nodeCode(p.nodeId)}</Td>
              <Td>{p.protocol}</Td>
              <Td mono>{p.portNumber}</Td>
              <Td><StatusBadge status={p.status} /></Td>
              <Td>{timeAgo(p.expiresAt)}</Td>
              <Td mono>{p.allocationId ? shortId(p.allocationId) : "—"}</Td>
            </tr>
          ))}
        </Table>
      )}
    </Card>
  );
}
