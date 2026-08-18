# dtunnel — Tunnel Management Platform

Implementation of [detail.md](detail.md) (spec v0.2): a control plane that decides
**who may tunnel, where, when, and under what policy**, while FRP only executes
approved tunnel state.

## Layout

| Path | What |
|---|---|
| `dtunnel/` | Control plane — Java 25 / Spring Boot 4.1, Postgres, Redis |
| `dtunnel-agent/` | User-side agent — Go 1.22, wraps `frpc` |
| `web/` | Dashboard — React 19, TanStack Router/Query, Tailwind v4 |
| `deploy/` | Compose stacks + per-node `frps.toml` templates |
| `progression.md` | Build progression tracker |

## Quick start (full stack)

```bash
cd deploy/compose
docker compose up -d --build        # postgres, redis, control-plane, frps-vn01
```

Control plane: http://localhost:8080 (Swagger at `/swagger-ui.html`).
Bootstrap SUPERADMIN: `admin@duox.local` / `admin-change-me` (override via
`DTUNNEL_SUPERADMIN_EMAIL` / `DTUNNEL_SUPERADMIN_PASSWORD`).

Seed a port pool, then run the web UI:

```bash
cd web && npm install && npm run dev   # http://localhost:3000 (proxies /api → :8080)
```

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

## Tests

```bash
cd dtunnel
JAVA_HOME=~/.jdks/jdk-25.0.4+7 ./mvnw test   # 12 tests incl. full business loop via Testcontainers
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
  expiry → 3-day grace → release; stale-agent detection at 60s).
- **Transport interface** (§4): REST now, gRPC later — swap touches one Go package.

## Environment notes

- JDK 25 (Temurin) at `~/.jdks/jdk-25.0.4+7`; Maven via `./mvnw`.
- frps/frpc pinned to **v0.71.0** (no `latest` tag on Docker Hub).
- frps server plugins cannot send custom headers → the plugin shared secret
  travels as `?token=` on the plugin path (set `DTUNNEL_FRP_PLUGIN_TOKEN`).
