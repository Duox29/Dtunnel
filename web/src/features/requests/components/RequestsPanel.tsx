import { useState } from "react";
import { Button, Card, CardTitle, Input, Select, StatusBadge, Table, Td, EmptyRow } from "../../../components/ui";
import { useNodes } from "../../nodes/hooks";
import { useRequests, useCreateRequest, useApproveRequest, useRejectRequest } from "../hooks";

export function RequestsPanel({ isAdmin }: { isAdmin: boolean }) {
  const nodes = useNodes();
  const requests = useRequests();
  const createRequest = useCreateRequest();
  const approve = useApproveRequest();
  const reject = useRejectRequest();

  const [nodeId, setNodeId] = useState("");
  const [port, setPort] = useState("");
  const [days, setDays] = useState("30");
  const [purpose, setPurpose] = useState("");

  const error =
    (createRequest.error instanceof Error && createRequest.error.message) ||
    (approve.error instanceof Error && approve.error.message) ||
    (reject.error instanceof Error && reject.error.message) ||
    "";

  const nodeCode = (id: string) => nodes.data?.find((n) => n.id === id)?.code ?? id.slice(0, 8);

  return (
    <Card>
      <CardTitle>Resource requests</CardTitle>
      <div className="mb-3 flex flex-wrap items-end gap-2">
        <Select value={nodeId} onChange={setNodeId}>
          <option value="">node…</option>
          {(nodes.data ?? []).map((n) => (
            <option key={n.id} value={n.id}>{n.code}</option>
          ))}
        </Select>
        <Input placeholder="preferred port (optional)" value={port} onChange={setPort} className="w-48" />
        <Input placeholder="days" value={days} onChange={setDays} className="w-20" />
        <Input placeholder="purpose" value={purpose} onChange={setPurpose} className="w-48" />
        <Button
          disabled={createRequest.isPending || !nodeId}
          onClick={() =>
            createRequest.mutate({
              nodeId,
              protocol: "TCP",
              preferredPort: port ? Number(port) : null,
              durationDays: Number(days) || 30,
              purpose,
            })
          }
        >
          Request port
        </Button>
      </div>
      {error && <p className="mb-2 text-sm text-bad">{error}</p>}
      <Table headers={["Node", "Proto", "Port", "Days", "Purpose", "Status", ""]}>
        {(requests.data ?? []).length === 0 && <EmptyRow cols={7} message="No requests yet" />}
        {(requests.data ?? []).map((r) => (
          <tr key={r.id}>
            <Td>{nodeCode(r.nodeId)}</Td>
            <Td>{r.protocol}</Td>
            <Td>{r.preferredPort ?? "any"}</Td>
            <Td>{r.durationDays}</Td>
            <Td>{r.purpose ?? "-"}</Td>
            <Td><StatusBadge status={r.status} /></Td>
            <Td>
              {isAdmin && r.status === "PENDING" && (
                <span className="flex gap-1">
                  <Button disabled={approve.isPending} onClick={() => approve.mutate(r.id)}>Approve</Button>
                  <Button variant="danger" disabled={reject.isPending} onClick={() => reject.mutate(r.id)}>Reject</Button>
                </span>
              )}
            </Td>
          </tr>
        ))}
      </Table>
    </Card>
  );
}
