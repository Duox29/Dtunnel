import { useCallback, useEffect, useState } from "react";
import { api, Me, NodeInfo, ResourceRequest, AgentInfo, TunnelInfo, PortInfo } from "./api";

export default function App() {
  const [me, setMe] = useState<Me | null>(null);
  const [authError, setAuthError] = useState("");

  useEffect(() => {
    api.me().then(setMe).catch(() => setMe(null));
  }, []);

  if (!me) {
    return <AuthForm onAuthed={setMe} error={authError} onError={setAuthError} />;
  }
  return <Dashboard me={me} onLogout={() => api.logout().then(() => setMe(null))} />;
}

function AuthForm({ onAuthed, error, onError }: { onAuthed: (m: Me) => void; error: string; onError: (e: string) => void }) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    onError("");
    try {
      const m = mode === "login" ? await api.login(email, password) : await api.register(email, password);
      onAuthed(m);
    } catch (err) {
      onError(String((err as Error).message));
    }
  };

  return (
    <div className="center-screen">
      <form className="card" onSubmit={submit}>
        <h1>dtunnel</h1>
        <p className="muted">Tunnel Management Platform</p>
        <label>Email <input value={email} onChange={e => setEmail(e.target.value)} type="email" required /></label>
        <label>Password <input value={password} onChange={e => setPassword(e.target.value)} type="password" required minLength={8} /></label>
        {error && <p className="error">{error}</p>}
        <button type="submit">{mode === "login" ? "Sign in" : "Create account"}</button>
        <button type="button" className="link" onClick={() => setMode(mode === "login" ? "register" : "login")}>
          {mode === "login" ? "Need an account? Register" : "Have an account? Sign in"}
        </button>
      </form>
    </div>
  );
}

function Dashboard({ me, onLogout }: { me: Me; onLogout: () => void }) {
  const [nodes, setNodes] = useState<NodeInfo[]>([]);
  const [requests, setRequests] = useState<ResourceRequest[]>([]);
  const [agents, setAgents] = useState<AgentInfo[]>([]);
  const [tunnels, setTunnels] = useState<TunnelInfo[]>([]);
  const [ports, setPorts] = useState<PortInfo[]>([]);
  const [notice, setNotice] = useState("");

  const refresh = useCallback(async () => {
    try {
      const [n, r, a, t, p] = await Promise.all([
        api.nodes(), api.requests(), api.agents(), api.tunnels(), api.ports(),
      ]);
      setNodes(n); setRequests(r); setAgents(a); setTunnels(t); setPorts(p);
    } catch (e) {
      setNotice(String((e as Error).message));
    }
  }, []);

  useEffect(() => { refresh(); const id = setInterval(refresh, 5000); return () => clearInterval(id); }, [refresh]);

  const isAdmin = me.role === "SUPERADMIN";
  const act = async (fn: () => Promise<unknown>) => {
    setNotice("");
    try { await fn(); await refresh(); } catch (e) { setNotice(String((e as Error).message)); }
  };

  return (
    <div className="dash">
      <header>
        <h1>dtunnel</h1>
        <span className="muted">{me.email} · {me.role}</span>
        <button className="link" onClick={onLogout}>Sign out</button>
      </header>
      {notice && <p className="error">{notice}</p>}

      {isAdmin && <AdminNodePanel nodes={nodes} onAction={act} />}

      <section className="card">
        <h2>Resource requests</h2>
        <RequestForm nodes={nodes} onSubmit={act} />
        <table>
          <thead><tr><th>Node</th><th>Proto</th><th>Port</th><th>Days</th><th>Status</th><th /></tr></thead>
          <tbody>
            {requests.map(r => (
              <tr key={r.id}>
                <td>{nodes.find(n => n.id === r.nodeId)?.code ?? r.nodeId.slice(0, 8)}</td>
                <td>{r.protocol}</td>
                <td>{r.preferredPort ?? "any"}</td>
                <td>{r.durationDays}</td>
                <td><Status s={r.status} /></td>
                <td>
                  {isAdmin && r.status === "PENDING" && <>
                    <button onClick={() => act(() => api.approveRequest(r.id))}>Approve</button>{" "}
                    <button onClick={() => act(() => api.rejectRequest(r.id))}>Reject</button>
                  </>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card">
        <h2>My ports</h2>
        <table>
          <thead><tr><th>Node</th><th>Proto</th><th>Port</th><th>Status</th><th>Expires</th><th>Allocation</th></tr></thead>
          <tbody>
            {ports.map(p => (
              <tr key={p.id}>
                <td>{nodes.find(n => n.id === p.nodeId)?.code ?? p.nodeId.slice(0, 8)}</td>
                <td>{p.protocol}</td>
                <td>{p.portNumber}</td>
                <td><Status s={p.status} /></td>
                <td>{p.expiresAt ? new Date(p.expiresAt).toLocaleDateString() : "-"}</td>
                <td className="mono">{p.allocationId?.slice(0, 8) ?? "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card">
        <h2>Agents</h2>
        <table>
          <thead><tr><th>ID</th><th>Platform</th><th>Status</th><th>Last seen</th><th /></tr></thead>
          <tbody>
            {agents.map(a => (
              <tr key={a.id}>
                <td className="mono">{a.id.slice(0, 8)}</td>
                <td>{a.platform}</td>
                <td><Status s={a.status} /></td>
                <td>{a.lastSeenAt ? new Date(a.lastSeenAt).toLocaleTimeString() : "-"}</td>
                <td>
                  {isAdmin && a.status === "PENDING" && <button onClick={() => act(() => api.approveAgent(a.id))}>Approve</button>}{" "}
                  {isAdmin && a.status !== "REVOKED" && <button onClick={() => act(() => api.revokeAgent(a.id))}>Revoke</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card">
        <h2>Tunnels</h2>
        <TunnelForm ports={ports} agents={agents} onSubmit={act} />
        <table>
          <thead><tr><th>Name</th><th>Target</th><th>Status</th><th /></tr></thead>
          <tbody>
            {tunnels.map(t => (
              <tr key={t.id}>
                <td>{t.name}</td>
                <td className="mono">{t.targetHost}:{t.targetPort}</td>
                <td><Status s={t.status} /></td>
                <td>
                  {["CONFIGURED", "STOPPED", "ERROR"].includes(t.status) && <button onClick={() => act(() => api.startTunnel(t.id))}>Start</button>}{" "}
                  {["ACTIVE", "STARTING"].includes(t.status) && <button onClick={() => act(() => api.stopTunnel(t.id))}>Stop</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function AdminNodePanel({ nodes, onAction }: { nodes: NodeInfo[]; onAction: (fn: () => Promise<unknown>) => void }) {
  const [code, setCode] = useState("");
  const [region, setRegion] = useState("");
  const [addr, setAddr] = useState("");
  return (
    <section className="card">
      <h2>Nodes (SUPERADMIN)</h2>
      <div className="row">
        <input placeholder="code (VN-01)" value={code} onChange={e => setCode(e.target.value)} />
        <input placeholder="region" value={region} onChange={e => setRegion(e.target.value)} />
        <input placeholder="public address" value={addr} onChange={e => setAddr(e.target.value)} />
        <button onClick={() => onAction(() => api.registerNode(code, region, addr))}>Register node</button>
      </div>
      <table>
        <thead><tr><th>Code</th><th>Region</th><th>Address</th><th>Status</th><th /></tr></thead>
        <tbody>
          {nodes.map(n => (
            <tr key={n.id}>
              <td>{n.code}</td><td>{n.region}</td><td className="mono">{n.publicAddress}</td>
              <td><Status s={n.status} /></td>
              <td><button onClick={() => onAction(() => api.seedPorts(n.id, "TCP", 20000, 20100))}>Seed TCP 20000-20100</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function RequestForm({ nodes, onSubmit }: { nodes: NodeInfo[]; onSubmit: (fn: () => Promise<unknown>) => void }) {
  const [nodeId, setNodeId] = useState("");
  const [port, setPort] = useState("");
  const [days, setDays] = useState("30");
  const [purpose, setPurpose] = useState("");
  return (
    <div className="row">
      <select value={nodeId} onChange={e => setNodeId(e.target.value)}>
        <option value="">node…</option>
        {nodes.map(n => <option key={n.id} value={n.id}>{n.code}</option>)}
      </select>
      <input placeholder="preferred port (optional)" value={port} onChange={e => setPort(e.target.value)} />
      <input placeholder="days" value={days} onChange={e => setDays(e.target.value)} />
      <input placeholder="purpose" value={purpose} onChange={e => setPurpose(e.target.value)} />
      <button onClick={() => onSubmit(() => api.createRequest(nodeId, "TCP", port ? Number(port) : null, Number(days), purpose))}>
        Request port
      </button>
    </div>
  );
}

function TunnelForm({ ports, agents, onSubmit }: { ports: PortInfo[]; agents: AgentInfo[]; onSubmit: (fn: () => Promise<unknown>) => void }) {
  const [alloc, setAlloc] = useState("");
  const [agentId, setAgentId] = useState("");
  const [name, setName] = useState("");
  const [host, setHost] = useState("127.0.0.1");
  const [port, setPort] = useState("");
  const usable = ports.filter(p => p.allocationId);
  return (
    <div className="row">
      <select value={alloc} onChange={e => setAlloc(e.target.value)}>
        <option value="">allocation…</option>
        {usable.map(p => <option key={p.id} value={p.allocationId!}>:{p.portNumber} ({p.protocol})</option>)}
      </select>
      <select value={agentId} onChange={e => setAgentId(e.target.value)}>
        <option value="">agent…</option>
        {agents.map(a => <option key={a.id} value={a.id}>{a.id.slice(0, 8)} ({a.platform})</option>)}
      </select>
      <input placeholder="name" value={name} onChange={e => setName(e.target.value)} />
      <input placeholder="target host" value={host} onChange={e => setHost(e.target.value)} />
      <input placeholder="target port" value={port} onChange={e => setPort(e.target.value)} />
      <button onClick={() => onSubmit(() => api.createTunnel(alloc, agentId, name, host, Number(port)))}>Create tunnel</button>
    </div>
  );
}

function Status({ s }: { s: string }) {
  const cls =
    ["ACTIVE", "ONLINE", "ALLOCATED", "APPROVED"].includes(s) ? "ok" :
    ["ERROR", "REVOKED", "REJECTED", "EXPIRED", "OFFLINE"].includes(s) ? "bad" : "warn";
  return <span className={`status ${cls}`}>{s}</span>;
}
