import { useState } from "react";
import { Button, Card, CardTitle, EmptyState, Input, Select, StatusBadge, Table, Td, useToast } from "../../../components/ui";
import { useNodes } from "../../nodes/hooks";
import { useRequests, useCreateRequest, useApproveRequest, useRejectRequest } from "../hooks";

export function RequestsPanel({ isAdmin }: { isAdmin: boolean }) {
  const nodes = useNodes();
  const requests = useRequests();
  const createRequest = useCreateRequest();
  const approve = useApproveRequest();
  const reject = useRejectRequest();
  const toast = useToast();

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
            createRequest.mutate(
              {
                nodeId,
                protocol: "TCP",
                preferredPort: port ? Number(port) : null,
                durationDays: Number(days) || 30,
                purpose,
              },
              {
                onSuccess: () => { toast("success", "Port request submitted"); setPort(""); setPurpose(""); },
                onError: (e) => toast("error", e instanceof Error ? e.message : "Request failed"),
              },
            )
          }
        >
          Request port
        </Button>
      </div>
      {error && <p className="mb-2 text-sm text-bad">{error}</p>}
      {(requests.data ?? []).length === 0 ? (
        <EmptyState title="No requests yet" body="Request a public port on a node to get started." />
      ) : (
      <Table headers={["Node", "Proto", "Port", "Days", "Purpose", "Status", ""]}>
        {(requests.data ?? []).map((r) => (
          <tr key={r.id} className="transition-colors hover:bg-hover/50">
            <Td>{nodeCode(r.nodeId)}</Td>
            <Td>{r.protocol}</Td>
            <Td>{r.preferredPort ?? "any"}</Td>
            <Td>{r.durationDays}</Td>
            <Td>{r.purpose ?? "-"}</Td>
            <Td><StatusBadge status={r.status} /></Td>
            <Td>
              {isAdmin && r.status === "PENDING" && (
                <span className="flex justify-end gap-1">
                  <Button
                    disabled={approve.isPending}
                    onClick={() => approve.mutate(r.id, {
                      onSuccess: () => toast("success", "Request approved — port allocated"),
                      onError: (e) => toast("error", e instanceof Error ? e.message : "Approve failed"),
                    })}
                  >
                    Approve
                  </Button>
                  <Button
                    variant="danger"
                    disabled={reject.isPending}
                    onClick={() => reject.mutate(r.id, {
                      onSuccess: () => toast("info", "Request rejected"),
                      onError: (e) => toast("error", e instanceof Error ? e.message : "Reject failed"),
                    })}
                  >
                    Reject
                  </Button>
                </span>
              )}
            </Td>
          </tr>
        ))}
      </Table>
      )}
    </Card>
  );
}
