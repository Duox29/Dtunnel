# dtunnel — Tunnel Management Platform

Implementation of [detail.md](detail.md) (spec v0.2): a control plane that decides
**who may tunnel, where, when, and under what policy**, while FRP only executes
approved tunnel state.

## Layout

| Path | What |
|---|---|
| `dtunnel/` | Control plane — Java 25 / Spring Boot 4.1, Postgres, Redis |
| `dtunnel-agent/` | Agents — Go 1.22: `duox-agent` (user side, wraps `frpc`) + `duox-node-agent` (gateway side, health/capacity) |
| `web/` | Dashboard — React 19, TanStack Router/Query, Tailwind v4 |
| `deploy/` | Compose stacks + per-node `frps.toml` + Caddy edge |
| `AUDIT.md` | detail.md compliance audit (section by section) |
| `progression.md` | Build progression tracker |

## Quick start (full stack)

```bash
cd deploy/compose
docker compose up -d --build   # postgres, redis, control-plane, web, frps-vn01, caddy
```

- Control plane: http://localhost:8080 (Swagger at `/swagger-ui.html`)
- Web dashboard: http://localhost:3000 (nginx SPA, proxies `/api` + `/agent`)
- HTTP tunnel edge: http://localhost:8090 (Caddy → frps vhost, Host-routed)
- Observability (optional): `cd deploy/observability && docker compose up -d`
  → Prometheus :9090, Grafana :3001, Loki :3100

Bootstrap SUPERADMIN: `admin@duox.local` / `admin-change-me` (override via
`DTUNNEL_SUPERADMIN_EMAIL` / `DTUNNEL_SUPERADMIN_PASSWORD`).

## The one business loop (Milestone 1)

1. User registers → submits a **resource request** (node, protocol, preferred port, days).
2. SUPERADMIN approves → port allocated (`FOR UPDATE SKIP LOCKED`, detail.md §5).
3. Agent registers its Ed25519 device key (`dtunnel-agent`) → SUPERADMIN approves the agent.
4. User creates a **tunnel** from the allocation → desired-state config version bumps.
5. Agent polls `/agent/v1/config`, renders `frpc.toml`, starts `frpc`.
6. `frps` fires **Login/NewProxy** hooks to `/agent/v1/frp-plugin` — the control plane
   allows/denies each attempt in real time (detail.md §9).
7. Agent heartbeat reports RUNNING → tunnel **ACTIVE**; traffic flows.

### Running the agent

```bash
cd dtunnel-agent && go build -o bin/duox-agent ./cmd/duox-agent
# first run registers the device (needs account credentials once):
./bin/duox-agent --server http://localhost:8080 \
  --email you@example.com --password 'your-password' \
  --frpc /path/to/frpc
```

Identity + token persist under `~/.config/duox-agent/` (detail.md §6).

### Running the Node Agent (gateway side)

The node agent runs on the machine hosting `frps` and reports health/capacity
(detail.md §1/§3.4). It authenticates with the per-node token issued at node
registration (SUPERADMIN: `POST /api/v1/nodes` → `nodeToken`; rotate via
`POST /api/v1/nodes/{id}/rotate-token`).

```bash
cd dtunnel-agent && go build -o bin/duox-node-agent ./cmd/duox-node-agent
./bin/duox-node-agent --server http://localhost:8080 \
  --token <node-token> --frps-admin http://127.0.0.1:7500
```

### HTTP tunnels (domain routing, detail.md §3.6)

HTTP tunnels are domain-routed over the node's shared frps vhost port — no port
allocation needed. Point DNS (or `/etc/hosts`) at the Caddy edge, then:

```bash
curl -b $COOKIE -X POST http://localhost:8080/api/v1/tunnels/http \
  -H 'Content-Type: application/json' \
  -d '{"nodeId":"<node>","agentId":"<agent>","name":"web",
       "domain":"app.example.com","targetHost":"127.0.0.1","targetPort":8000}'
```

The agent's `frpc` registers the domain on the node's vhost port; Caddy
terminates TLS (automatic in production) and forwards by Host header. The FRP
plugin rejects any proxy claiming a domain that isn't the tunnel's registered
one.

## Tests

```bash
cd dtunnel
JAVA_HOME=~/.jdks/jdk-25.0.4+7 ./mvnw test   # 16 tests incl. full business loop via Testcontainers
```

Note: Docker Engine 29 requires API ≥1.44; the pom passes `-Dapi.version=1.44`
to surefire (property `docker.api.version`).

## Key design points (from detail.md)

- **Server is authoritative** (§1): the agent/frpc never decide; every Login/NewProxy
  is re-authorized by the control plane.
- **Device identity = Ed25519 public key** (§6); short-lived Redis-backed session
  tokens afterwards — a leaked token can't claim arbitrary ports.
- **Port allocation** (§5): `SELECT … FOR UPDATE SKIP LOCKED` + partial unique index
  on live states.
- **frps deployed once per node** (§9): per-tunnel changes never touch frps config;
  the httpPlugins callback does all authorization.
- **Cluster-safe jobs** (§10): `@Scheduled` + ShedLock (expiration warnings →
  expiry → 3-day grace → release; stale-agent detection at 60s; stale-node at 120s).
- **Transport interface** (§4): REST now, gRPC later — swap touches one Go package.
- **Node Agent** (§1/§16): separate `duox-node-agent` binary with per-node
  shared-secret auth — the gateway host never carries user device credentials.
- **HTTP/HTTPS edge** (§3.6): Caddy → frps vhost; domain claims are re-authorized
  by the plugin on every NewProxy, exactly like port claims.

## Environment notes

- JDK 25 (Temurin) at `~/.jdks/jdk-25.0.4+7`; Maven via `./mvnw`.
- frps/frpc pinned to **v0.71.0** (no `latest` tag on Docker Hub).
- frps server plugins cannot send custom headers → the plugin shared secret
  travels as `?token=` on the plugin path (set `DTUNNEL_FRP_PLUGIN_TOKEN`).
