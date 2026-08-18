import { useState } from "react";
import { Button, Card, CardTitle, Input, StatusBadge, Table, Td, EmptyRow } from "../../../components/ui";
import { useNodes, useRegisterNode, useSeedPorts, useUpdateNode } from "../hooks";

export function NodesPanel() {
  const nodes = useNodes();
  const registerNode = useRegisterNode();
  const seedPorts = useSeedPorts();
  const updateNode = useUpdateNode();
  const [code, setCode] = useState("");
  const [region, setRegion] = useState("");
  const [addr, setAddr] = useState("");
  const [adminUrl, setAdminUrl] = useState("");
  const [editingUrl, setEditingUrl] = useState<Record<string, string>>({});

  const error =
    (registerNode.error instanceof Error && registerNode.error.message) ||
    (seedPorts.error instanceof Error && seedPorts.error.message) ||
    (updateNode.error instanceof Error && updateNode.error.message) ||
    "";

  return (
    <Card>
      <CardTitle>Nodes (SUPERADMIN)</CardTitle>
      <div className="mb-3 flex flex-wrap items-end gap-2">
        <Input label="Code" placeholder="VN-01" value={code} onChange={setCode} className="w-28" />
        <Input label="Region" placeholder="vietnam" value={region} onChange={setRegion} className="w-32" />
        <Input label="Public address" placeholder="203.0.113.10" value={addr} onChange={setAddr} className="w-40" />
        <Input label="frps admin URL" placeholder="http://frps-vn01:7500" value={adminUrl} onChange={setAdminUrl} className="w-52" />
        <Button
          disabled={registerNode.isPending || !code || !region || !addr}
          onClick={() =>
            registerNode.mutate(
              { code, region, publicAddress: addr, frpsAdminUrl: adminUrl || undefined },
              { onSuccess: () => { setCode(""); setRegion(""); setAddr(""); setAdminUrl(""); } },
            )
          }
        >
          Register node
        </Button>
      </div>
      {error && <p className="mb-2 text-sm text-bad">{error}</p>}
      <Table headers={["Code", "Region", "Address", "frps admin", "Protocols", "Status", ""]}>
        {(nodes.data ?? []).length === 0 && <EmptyRow cols={7} message="No nodes registered yet" />}
        {(nodes.data ?? []).map((n) => (
          <tr key={n.id}>
            <Td>{n.code}</Td>
            <Td>{n.region}</Td>
            <Td mono>{n.publicAddress}</Td>
            <Td mono>{n.frpsAdminUrl ?? <span className="text-ink-dim">not set</span>}</Td>
            <Td>{n.protocolCapabilities.join(", ")}</Td>
            <Td><StatusBadge status={n.status} /></Td>
            <Td>
              <div className="flex gap-1">
                <Button
                  disabled={seedPorts.isPending}
                  onClick={() => seedPorts.mutate({ nodeId: n.id, protocol: "TCP", start: 20000, end: 20100 })}
                >
                  Seed TCP 20000–20100
                </Button>
                <Input
                  placeholder={n.frpsAdminUrl ?? "frps admin URL"}
                  value={editingUrl[n.id] ?? ""}
                  onChange={(v) => setEditingUrl((s) => ({ ...s, [n.id]: v }))}
                  className="w-44"
                />
                <Button
                  variant="ghost"
                  disabled={updateNode.isPending || !(editingUrl[n.id] ?? "").trim()}
                  onClick={() =>
                    updateNode.mutate(
                      { nodeId: n.id, patch: { frpsAdminUrl: (editingUrl[n.id] ?? "").trim() } },
                      { onSuccess: () => setEditingUrl((s) => ({ ...s, [n.id]: "" })) },
                    )
                  }
                >
                  Set
                </Button>
              </div>
            </Td>
          </tr>
        ))}
      </Table>
    </Card>
  );
}
