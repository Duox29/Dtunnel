import { useState } from "react";
import { Button, Card, CardTitle, Input, StatusBadge, Table, Td, EmptyRow } from "../../../components/ui";
import { useNodes, useRegisterNode, useSeedPorts } from "../hooks";

export function NodesPanel() {
  const nodes = useNodes();
  const registerNode = useRegisterNode();
  const seedPorts = useSeedPorts();
  const [code, setCode] = useState("");
  const [region, setRegion] = useState("");
  const [addr, setAddr] = useState("");

  const error =
    (registerNode.error instanceof Error && registerNode.error.message) ||
    (seedPorts.error instanceof Error && seedPorts.error.message) ||
    "";

  return (
    <Card>
      <CardTitle>Nodes (SUPERADMIN)</CardTitle>
      <div className="mb-3 flex flex-wrap items-end gap-2">
        <Input label="Code" placeholder="VN-01" value={code} onChange={setCode} className="w-32" />
        <Input label="Region" placeholder="vietnam" value={region} onChange={setRegion} className="w-36" />
        <Input label="Public address" placeholder="203.0.113.10" value={addr} onChange={setAddr} className="w-44" />
        <Button
          disabled={registerNode.isPending || !code || !region || !addr}
          onClick={() => registerNode.mutate({ code, region, publicAddress: addr }, { onSuccess: () => { setCode(""); setRegion(""); setAddr(""); } })}
        >
          Register node
        </Button>
      </div>
      {error && <p className="mb-2 text-sm text-bad">{error}</p>}
      <Table headers={["Code", "Region", "Address", "Protocols", "Status", ""]}>
        {(nodes.data ?? []).length === 0 && <EmptyRow cols={6} message="No nodes registered yet" />}
        {(nodes.data ?? []).map((n) => (
          <tr key={n.id}>
            <Td>{n.code}</Td>
            <Td>{n.region}</Td>
            <Td mono>{n.publicAddress}</Td>
            <Td>{n.protocolCapabilities.join(", ")}</Td>
            <Td><StatusBadge status={n.status} /></Td>
            <Td>
              <Button
                disabled={seedPorts.isPending}
                onClick={() => seedPorts.mutate({ nodeId: n.id, protocol: "TCP", start: 20000, end: 20100 })}
              >
                Seed TCP 20000–20100
              </Button>
            </Td>
          </tr>
        ))}
      </Table>
    </Card>
  );
}
