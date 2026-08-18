import { useState } from "react";
import { Button, Card, CardTitle, CopyButton, EmptyState, Input, StatusBadge, Table, Td, useToast } from "../../../components/ui";
import { useNodes, useRegisterNode, useSeedPorts, useUpdateNode } from "../hooks";

export function NodesPanel() {
  const nodes = useNodes();
  const registerNode = useRegisterNode();
  const seedPorts = useSeedPorts();
  const updateNode = useUpdateNode();
  const toast = useToast();
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

  const list = nodes.data ?? [];

  return (
    <Card>
      <CardTitle>Nodes (SUPERADMIN)</CardTitle>
      <div className="mb-4 rounded-lg border border-edge-soft bg-panel-2 p-3">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-ink-dim">Register gateway node</p>
        <div className="flex flex-wrap items-end gap-2">
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            Code
            <Input placeholder="VN-01" value={code} onChange={setCode} className="w-28" />
          </label>
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            Region
            <Input placeholder="vietnam" value={region} onChange={setRegion} className="w-32" />
          </label>
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            Public address
            <Input placeholder="203.0.113.10" value={addr} onChange={setAddr} className="w-40" />
          </label>
          <label className="flex flex-col gap-1 text-xs text-ink-dim">
            frps admin URL
            <Input placeholder="http://frps-vn01:7500" value={adminUrl} onChange={setAdminUrl} className="w-52" />
          </label>
          <Button
            disabled={registerNode.isPending || !code || !region || !addr}
            onClick={() =>
              registerNode.mutate(
                { code, region, publicAddress: addr, frpsAdminUrl: adminUrl || undefined },
                {
                  onSuccess: () => {
                    toast("success", `Node ${code} registered`);
                    setCode(""); setRegion(""); setAddr(""); setAdminUrl("");
                  },
                  onError: (e) => toast("error", e instanceof Error ? e.message : "Register failed"),
                },
              )
            }
          >
            Register node
          </Button>
        </div>
      </div>
      {error && <p className="mb-2 text-sm text-bad">{error}</p>}
      {list.length === 0 ? (
        <EmptyState title="No nodes registered yet" body="Register a gateway node, then seed its port range." />
      ) : (
        <Table headers={["Code", "Region", "Address", "frps admin", "Protocols", "Status", ""]}>
          {list.map((n) => (
            <tr key={n.id} className="transition-colors hover:bg-hover/50">
              <Td>
                <span className="flex items-center gap-1.5">
                  <span className="font-medium">{n.code}</span>
                  <CopyButton text={n.id} label="" />
                </span>
              </Td>
              <Td>{n.region}</Td>
              <Td mono>{n.publicAddress}</Td>
              <Td mono>{n.frpsAdminUrl ?? <span className="text-ink-faint">not set</span>}</Td>
              <Td>{n.protocolCapabilities.join(", ")}</Td>
              <Td><StatusBadge status={n.status} /></Td>
              <Td>
                <div className="flex justify-end items-center gap-1">
                  <Button
                    variant="ghost"
                    disabled={seedPorts.isPending}
                    onClick={() =>
                      seedPorts.mutate(
                        { nodeId: n.id, protocol: "TCP", start: 20000, end: 20100 },
                        {
                          onSuccess: () => toast("success", `Seeded TCP 20000–20100 on ${n.code}`),
                          onError: (e) => toast("error", e instanceof Error ? e.message : "Seed failed"),
                        },
                      )
                    }
                  >
                    Seed ports
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
                        {
                          onSuccess: () => {
                            toast("success", `frps admin URL updated on ${n.code}`);
                            setEditingUrl((s) => ({ ...s, [n.id]: "" }));
                          },
                          onError: (e) => toast("error", e instanceof Error ? e.message : "Update failed"),
                        },
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
      )}
    </Card>
  );
}
