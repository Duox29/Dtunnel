# Tunnel Management Platform — Optimized Technical Specification

**Status:** Optimized / Build-Ready
**Version:** 0.2 (supersedes v0.1 draft)
**Date:** 2026-08-18

---

## 0. What Changed From v0.1 and Why

The original draft (v0.1) already got the hard part right: the Control Plane / Data Plane split, the resource-lifecycle-before-tunnel-instance model, and "server is authoritative, agent executes" are all correct architectural calls and are kept unchanged. This revision does not replace that thinking — it tightens six things that were underspecified or would create rework later if left as-is:

| # | Area | v0.1 | v0.2 optimization |
|---|------|------|--------------------|
| 1 | FRP integration | Agent pushes full config; unclear how `frps` learns about new tunnels | Use **FRP's native HTTP server-plugin hooks** (`Login`, `NewProxy`, `Ping`) so `frps` asks the control plane in real time — no per-tunnel `frps` redeploy, no custom protocol needed |
| 2 | Agent transport | REST + heartbeat now, gRPC/WebSocket "maybe Phase 2" | Keep REST for MVP (correct call for speed) but isolate it behind a transport interface so swapping to gRPC streaming later touches one module, not the domain model |
| 3 | Control-plane runtime | Spring Boot, unspecified threading model | Java 21 **virtual threads** (Project Loom) — blocking, simple code, scales to thousands of concurrent agent connections without reactive (WebFlux) complexity |
| 4 | Redis | "Optional" | Promoted to **required from MVP** — it's the backing store for sessions, distributed rate limiting, and the offline-agent detection window, all of which are MVP requirements already listed elsewhere in the spec |
| 5 | Port allocation | "Transaction + unique constraint" (correct but abstract) | Concrete `SELECT ... FOR UPDATE SKIP LOCKED` + partial unique index pattern, given below as real DDL |
| 6 | Scheduled jobs | Listed, no concurrency safety note | Added **ShedLock** — without it, running two control-plane instances double-fires expiration/release jobs |

Everything below is the resulting full spec: architecture, tech stack, schema, API, FRP integration design, repo layout, and a concrete build order.

---

## 1. Core Architecture

Unchanged principle, restated because everything else derives from it:

> **The platform decides who may tunnel, where, when, and under what policy. The tunnel engine only executes the approved tunnel state.**

Refined component diagram — the key addition is a **Gateway Node Agent**, distinct from the **User Agent**, because the original spec conflated "the machine running `frps`" with "the machine running the user's service." They have different responsibilities:

```text
                              React Web UI
                                   │ HTTPS
                                   ▼
                    ┌───────────────────────────┐
                    │      Control Plane          │
                    │  (Spring Boot, Java 21)     │
                    │                              │
                    │  Auth · RBAC · Port Manager  │
                    │  Request Mgr · Node Mgr      │
                    │  Agent Mgr · Config Mgr      │
                    │  Quota/Usage · Audit         │
                    └──────────┬───────────────────┘
                               │
             ┌─────────────────┼──────────────────────┐
             │                 │                       │
        Postgres            Redis                  Prometheus
        (source of truth)   (sessions, locks,       (metrics)
                             rate limits)
                               │
                 Agent-facing API (REST, MVP)
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                       ▼
 ┌─────────────┐        ┌─────────────┐         ┌─────────────┐
 │ Gateway Node │        │ Gateway Node │        │ Gateway Node │
 │   VN-01      │        │   SG-01      │        │   JP-01      │
 │              │        │              │        │              │
 │ frps         │◄─plugin│ frps         │        │ frps         │
 │ (allowPorts  │  hooks │              │        │              │
 │  range only) │        │              │        │              │
 │ Node Agent   │        │ Node Agent   │        │ Node Agent   │
 │ (health/     │        │              │        │              │
 │  metrics)    │        │              │        │              │
 └──────▲───────┘        └──────▲───────┘         └──────▲──────┘
        │ frpc connects to whichever node the tunnel targets
        │
   ┌────┴────┐
   │  User    │   (Linux / Windows / macOS)
   │  Agent    │
   │ (frpc +  │
   │  control │
   │  client) │
   └──────────┘
```

Why this matters: `frps` itself almost never needs a config push per tunnel. Configure it once with a broad `allowPorts` range and TCP mux; then use the **server plugin** mechanism (Section 12) so `frps` calls back to the control plane on every `Login` and `NewProxy` event to authorize it in real time. This removes an entire class of "how do we hot-reload frps across N nodes" problems that the v0.1 draft left unresolved.

---

## 2. Actors, Domain Model, Lifecycle States

These are unchanged from v0.1 and are correct — restated briefly for completeness; see Section 9 for the schema that implements them.

- **Actors:** `USER`, `SUPERADMIN` (MVP only — no Admin/Operator/Viewer yet).
- **Domain:** `User → Agent → Credential`, `User → ResourceRequest → PortAllocation → Port`, `User → TunnelInstance → {Node, Target, Protocol, QoSPolicy, Lifecycle}`.
- **Key rule kept as-is:** a port allocation and a tunnel instance are separate concepts — a user can own a port with no tunnel running against it.
- **State machines kept as-is:** Resource Request (`DRAFT→SUBMITTED→PENDING→APPROVED/REJECTED→ALLOCATED`), Port (`AVAILABLE→RESERVED→ALLOCATED→ACTIVE→EXPIRED_PENDING_RELEASE→RELEASED/DISABLED`), Tunnel (`CREATED→CONFIGURED→STARTING→ACTIVE→STOPPING→STOPPED`, with `ERROR` and `EXPIRING→EXPIRED` branches).
- **Expiration rule kept as-is:** 5-day warning → expiry → service stopped → 3-day grace hold on the port → release.

---

## 3. Final Tech Stack

### 3.1 Control Plane — Java 21 LTS + Spring Boot 3.3+

Kept from v0.1 (matches stated team skillset) with concrete additions:

```text
Spring Boot 3.3+
Spring Web (MVC, blocking — see virtual threads note below)
Spring Security 6 (form login + OAuth2 client for Google)
Spring Data JPA + Hibernate
Spring Validation
Flyway                    → schema migrations (NEW — v0.1 had no migration tool)
MapStruct                 → DTO↔entity mapping, cuts controller/service boilerplate (NEW)
ShedLock                  → cluster-safe @Scheduled jobs (NEW, required once >1 instance runs)
Bucket4j + Redis          → distributed API rate limiting (NEW, implements v0.1 §37 concretely)
Springdoc OpenAPI         → OpenAPI 3 docs generation
Testcontainers            → integration tests against real Postgres/Redis (NEW)
grpc-spring-boot-starter  → only if/when the agent channel moves to gRPC (Phase 2 hook, see §4)
```

**Concurrency model:** run on **Java 21 virtual threads** (`spring.threads.virtual.enabled=true`). This lets the control plane use the simple blocking Spring MVC + JPA programming model while still handling thousands of concurrent agent heartbeats/long-polls cheaply — avoids taking on WebFlux/reactive complexity that a control-plane-with-business-rules generally doesn't need.

### 3.2 Database — PostgreSQL 16

Kept from v0.1. JSONB for `configuration_versions.payload` and `audits.metadata`. Partial unique indexes for port allocation (Section 9).

### 3.3 Cache / Fast State — Redis 7 (promoted from "optional" to required)

Justification: sessions, rate limiting, and "agent offline after ~1 min without heartbeat" all need a fast expiring store, and all three are MVP requirements per the original spec — so Redis is on the MVP critical path regardless of how it's labeled. Making that explicit now avoids a mid-MVP surprise dependency add.

### 3.4 Agent — Go 1.22+

Kept from v0.1. Two logical binaries from one module:

```text
duox-agent        → runs on the user's machine, wraps frpc, does control-plane auth/config-sync/heartbeat
duox-node-agent    → runs on gateway nodes, wraps frps lifecycle + reports node health/capacity metrics
```

Add **goreleaser** for cross-compiled release builds (Linux/Windows now, macOS when distribution effort allows, per v0.1 MVP client scope).

### 3.5 Frontend — React + TypeScript + Vite

Concrete picks where v0.1 said "a mature component library":

```text
React 18, TypeScript, Vite
shadcn/ui + Tailwind CSS   → component layer + design tokens, not a heavy pre-styled kit
TanStack Query              → server state / caching
TanStack Router             → routing
Recharts                    → traffic/usage charts (simpler API than ECharts for this scope)
```

### 3.6 Edge / HTTP-HTTPS

Kept from v0.1: Caddy preferred for MVP+ (automatic TLS, simplest config) over Traefik/Nginx, deferred to Phase 2 per v0.1 scope (TCP/UDP only in MVP).

### 3.7 Observability

Completing v0.1's "structured JSON logs" into a concrete self-hosted stack (PLG):

```text
Prometheus   → metrics (control plane, node agents export /metrics)
Loki         → log aggregation (NEW — completes the stack; JSON logs alone aren't queryable at scale)
Grafana      → dashboards for both
OpenTelemetry → application traces
```

### 3.8 Deployment

Kept from v0.1: Docker Compose for MVP. Add a minimal GitHub Actions CI pipeline (build + test + Docker image push) from day one — not listed in v0.1 but needed the moment there's more than one contributor.

### 3.9 Tunnel Engine — FRP

Kept from v0.1, integration model concretized in Section 12.

---

## 4. Agent ↔ Control-Plane Channel

**Decision: REST + short heartbeat for MVP**, matching v0.1's own MVP-speed philosophy — but with one structural change: put the transport behind an interface (`AgentTransport`) in both the Go agent and the Java control plane's agent-facing layer, mirroring the FRP-adapter isolation principle the original spec already applies to the tunnel engine.

Why this matters concretely: "fast revocation" is a named security requirement (v0.1 §38), and pure polling bounds revocation latency to the heartbeat interval. Two mitigations that don't require gRPC in MVP:

- Set heartbeat interval to **15–20s** (not minutes) — bounds worst-case revocation propagation without new infrastructure.
- Because the transport is behind an interface, moving to gRPC bidirectional streaming in Phase 2 (for push-config + sub-second revocation) is a transport-layer swap, not a redesign of the reconciler, domain model, or agent state machine.

---

## 5. Port Allocation Concurrency (concrete)

v0.1 correctly identified the race but stayed abstract. Concrete pattern:

```sql
-- Uniqueness is enforced only for "live" states — released/disabled ports
-- must be re-allocatable, so the constraint is partial, not global.
CREATE UNIQUE INDEX uq_port_live
  ON ports (node_id, protocol, port_number)
  WHERE status IN ('RESERVED', 'ALLOCATED', 'ACTIVE');
```

Allocation transaction:

```sql
BEGIN;
SELECT * FROM ports
 WHERE node_id = $1 AND protocol = $2 AND port_number = ANY($3::int[])
   AND status = 'AVAILABLE'
 ORDER BY port_number
 FOR UPDATE SKIP LOCKED
 LIMIT 1;
-- application verifies policy/ownership on the returned row
UPDATE ports SET status = 'ALLOCATED', owner_user_id = $4 WHERE id = $5;
INSERT INTO port_allocations (...) VALUES (...);
COMMIT;
```

`FOR UPDATE SKIP LOCKED` means two concurrent requests for the same preferred port never block each other into a deadlock — the loser simply moves to the next candidate in its suggestion list (v0.1 §8.3's port-suggestion UX maps directly onto this).

---

## 6. Device Identity / Free-Tier Anti-Abuse (concrete)

v0.1 correctly ruled out single spoofable identifiers. Concrete mechanism:

1. On first run, the agent generates a local Ed25519 keypair and persists it in an OS-appropriate secure location (`%APPDATA%` / `~/.config`).
2. The **public key is the durable device identity** — it's what gets bound to a user account, rate-limited, and revoked. It survives reinstalls of the agent but not reformatting the machine (an acceptable, disclosed trade-off — no single hardware ID is used, per v0.1's own constraint).
3. Registration combines: device public key + email verification + Redis-backed IP/ASN rate limiting + a cooldown on release-then-recreate cycling (v0.1 §36 already lists these signals; this just makes the primary one concrete and load-bearing rather than leaving all signals equally weighted).
4. A leaked agent credential (the session token issued after key-based auth) is short-lived and scoped to that one device's permitted tunnels — consistent with v0.1 §12.3's "leaked credential must not grant arbitrary rights" rule.

---

## 7. Full Database Schema (DDL sketch)

```sql
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT,              -- null if Google-only
  google_subject TEXT UNIQUE,
  role TEXT NOT NULL DEFAULT 'USER', -- USER | SUPERADMIN
  plan TEXT NOT NULL DEFAULT 'FREE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  email_verified_at TIMESTAMPTZ
);

CREATE TABLE agents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  public_key TEXT UNIQUE NOT NULL,     -- device identity, §6
  platform TEXT NOT NULL,              -- linux | windows | macos
  agent_version TEXT,
  last_seen_at TIMESTAMPTZ,
  status TEXT NOT NULL DEFAULT 'PENDING', -- PENDING|ONLINE|OFFLINE|REVOKED
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE nodes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT UNIQUE NOT NULL,           -- VN-01, SG-01...
  region TEXT NOT NULL,
  public_address TEXT NOT NULL,
  protocol_capabilities TEXT[] NOT NULL DEFAULT '{TCP,UDP}',
  capacity_json JSONB,
  status TEXT NOT NULL DEFAULT 'OFFLINE'
);

CREATE TABLE ports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  node_id UUID NOT NULL REFERENCES nodes(id),
  protocol TEXT NOT NULL,              -- TCP | UDP
  port_number INT NOT NULL,
  status TEXT NOT NULL DEFAULT 'AVAILABLE',
  owner_user_id UUID REFERENCES users(id),
  reserved_range_id UUID
);
CREATE UNIQUE INDEX uq_port_live ON ports (node_id, protocol, port_number)
  WHERE status IN ('RESERVED','ALLOCATED','ACTIVE');

CREATE TABLE resource_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  node_id UUID NOT NULL REFERENCES nodes(id),
  protocol TEXT NOT NULL,
  preferred_port INT,
  duration_days INT NOT NULL DEFAULT 30,
  purpose TEXT,
  status TEXT NOT NULL DEFAULT 'DRAFT',
  reviewed_by UUID REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE port_allocations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  port_id UUID NOT NULL REFERENCES ports(id),
  request_id UUID REFERENCES resource_requests(id),
  user_id UUID NOT NULL REFERENCES users(id),
  allocated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  grace_expires_at TIMESTAMPTZ
);

CREATE TABLE tunnels (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  agent_id UUID NOT NULL REFERENCES agents(id),
  port_allocation_id UUID NOT NULL REFERENCES port_allocations(id),
  name TEXT NOT NULL,
  target_host TEXT NOT NULL,
  target_port INT NOT NULL,
  bandwidth_limit_mbps INT,
  max_connections INT,
  status TEXT NOT NULL DEFAULT 'CREATED'
);

CREATE TABLE configuration_versions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_id UUID NOT NULL REFERENCES agents(id),
  version INT NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (agent_id, version)
);

CREATE TABLE usage_records (
  id BIGSERIAL PRIMARY KEY,
  tunnel_id UUID NOT NULL REFERENCES tunnels(id),
  bytes_in BIGINT NOT NULL DEFAULT 0,
  bytes_out BIGINT NOT NULL DEFAULT 0,
  active_seconds INT NOT NULL DEFAULT 0,
  bucket_start TIMESTAMPTZ NOT NULL
);

CREATE TABLE audits (
  id BIGSERIAL PRIMARY KEY,
  actor TEXT NOT NULL,
  actor_type TEXT NOT NULL,       -- USER|ADMIN|AGENT|SYSTEM
  action TEXT NOT NULL,
  resource_type TEXT,
  resource_id TEXT,
  result TEXT NOT NULL,
  source_ip TEXT,
  metadata JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Managed with Flyway migrations (`V1__init.sql`, `V2__...`), not hand-applied SQL.

---

## 8. API Design

Split cleanly by consumer, per v0.1's own note that agent-facing and human-facing APIs should be logically separated — made explicit with distinct base paths and auth schemes:

```text
/api/v1/*        human/session-cookie auth (web UI)
/agent/v1/*       device-credential auth (agents)
```

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/auth/login` | email/password, sets httpOnly session cookie |
| GET  | `/api/v1/auth/google` | OAuth2 redirect flow |
| GET  | `/api/v1/nodes` | nodes the caller is permitted to see |
| POST | `/api/v1/nodes/{id}/ping` | latency test trigger, UX only |
| POST | `/api/v1/resource-requests` | create |
| POST | `/api/v1/resource-requests/{id}/approve` | SUPERADMIN only |
| POST | `/api/v1/resource-requests/{id}/reject` | SUPERADMIN only |
| GET  | `/api/v1/ports` | scoped to caller unless SUPERADMIN |
| POST | `/api/v1/tunnels` | create from an owned allocation |
| POST | `/api/v1/tunnels/{id}/start` / `/stop` | |
| GET  | `/agent/v1/config/version` | current desired version number only |
| GET  | `/agent/v1/config` | full desired-state payload |
| POST | `/agent/v1/heartbeat` | liveness + reported running version |
| POST | `/agent/v1/register` | first-run device registration, §6 |

Why session cookies over JWT-in-localStorage for the web UI: httpOnly cookies aren't readable by JS, closing the common XSS-token-theft path that bare JWTs in `localStorage` are exposed to. Session store is Redis (§3.3).

---

## 9. FRP Integration Design (the core technical unlock)

This is the single biggest concrete improvement over v0.1. FRP already ships an HTTP **server plugin** mechanism supporting `Login`, `NewProxy`, `CloseProxy`, `Ping`, `NewWorkConn`, and `NewUserConn` hooks — `frps` calls out to an HTTP endpoint on each of these events and the endpoint returns allow/deny plus optional overrides. <cite index="3-1">The plugin address, path, and which operations it handles are configured directly in frps.toml.</cite>

Design:

```text
frps.toml (per node, static, rarely changes):
  bindPort = 7000
  allowPorts = [{ start = 20000, end = 60000 }]   -- broad range owned by this node
  [[httpPlugins]]
    name = "duox-authz"
    addr = "https://control-plane.internal/agent/v1/frp-plugin"
    ops  = ["Login", "NewProxy", "Ping"]
```

Flow when a tunnel goes `CONFIGURED → STARTING`:

```text
1. Control plane approves allocation, writes tunnel row (status STARTING)
2. Agent polls /agent/v1/config, sees new tunnel, starts frpc with that proxy definition
3. frpc connects to frps → frps fires Login hook → control plane validates the agent credential
4. frpc requests remotePort=25565 → frps fires NewProxy hook → control plane checks:
     - does this port_allocation belong to this agent/user?
     - is the allocation still ACTIVE (not expired)?
     - does it match node_id/protocol/port_number on file?
   → allow or deny, in real time, per attempt
5. frps admits or rejects the proxy — no frps config file was touched
6. Agent reports APPLIED; control plane marks tunnel ACTIVE
```

This means: **`frps` is deployed once per node and essentially never reconfigured for individual tunnels.** All authorization logic lives in the control plane, exactly matching the "platform decides, engine executes" principle — but now with a concrete, already-built FRP feature backing it instead of a bespoke config-push protocol. <cite index="1-1">frps.toml's allowPorts setting is what prevents users from claiming ports outside the range the platform intends to hand out</cite>, which is the node-level backstop behind the plugin's per-request checks.

`CloseProxy` and `Ping` hooks are used for: reporting a proxy closed (feeds `tunnels.status`), and periodic liveness (cheap alternative/supplement to the agent's own heartbeat).

The Go agent's `frp` adapter package (per the repo layout below) is responsible only for: translating a `Tunnel` domain object into an `frpc` proxy block, and starting/stopping/reloading the local `frpc` process. It does not implement authorization — that lives entirely server-side, which is what keeps a leaked agent credential from being sufficient to claim arbitrary ports (v0.1 §12.3, §38).

---

## 10. Background Jobs (cluster-safe)

All jobs from v0.1 §33 kept, with `@Scheduled` + `@SchedulerLock` (ShedLock) so a second control-plane instance never double-processes the same expiration batch:

```text
processExpirationWarnings()   -- 5 days before expiry
processExpirations()          -- stop service, start grace period
processGraceReleases()        -- after 3 days, release port
detectStaleAgents()           -- no heartbeat/Ping ~60s → OFFLINE
aggregateUsage()               -- roll up usage_records
reconcileDesiredVsObserved()   -- see §11
```

Each is idempotent by construction (state-machine transitions are checked, not blindly applied), matching v0.1's own idempotency requirement.

---

## 11. Reconciliation Loop

Kept from v0.1, stated as a concrete periodic job rather than an abstract concept:

```text
every N seconds, per active tunnel:
  desired = tunnels.status (DB)
  observed = last-reported status from agent heartbeat / frps CloseProxy events
  if desired == ACTIVE and observed == STOPPED for > threshold:
      → flag for investigation / attempt restart command on next agent poll
```

This gives Kubernetes-controller-like consistency without requiring Kubernetes, exactly as v0.1 intended — the addition here is just making the comparison job concrete instead of conceptual.

---

## 12. Repository Structure

```text
tunnel-platform/
├── control-plane/                 (Java 21 / Spring Boot)
│   ├── api/                       controllers, DTOs
│   ├── domain/                    entities, state machines
│   ├── application/                services (ResourceRequestService, PortAllocationPolicy, ...)
│   ├── infrastructure/             JPA repos, Redis, FRP-plugin webhook endpoint
│   └── bootstrap/                  main, config, Flyway migrations
│
├── agent/                          (Go)
│   ├── cmd/duox-agent/             user-side binary entrypoint
│   ├── cmd/duox-node-agent/        gateway-node binary entrypoint
│   ├── control/                    AgentTransport interface + REST impl (§4)
│   ├── identity/                   keypair generation/storage (§6)
│   ├── config/                     desired-state fetch, version compare
│   ├── runtime/                    reconcile loop, process supervision
│   └── frp/                        adapter: domain Tunnel → frpc proxy block
│
├── web/                             (React/TS/Vite)
│   ├── src/features/                by domain: tunnels, agents, requests, admin
│   ├── src/components/
│   └── src/routes/
│
├── deploy/
│   ├── docker/                     Dockerfiles per component
│   ├── compose/                    docker-compose.yml for local + MVP prod
│   └── frps/                        frps.toml template with httpPlugins block
│
└── docs/
```

---

## 13. Minimal `docker-compose.yml` Skeleton (MVP)

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: tunnelplatform
      POSTGRES_PASSWORD: change_me
    volumes: ["pgdata:/var/lib/postgresql/data"]

  redis:
    image: redis:7

  control-plane:
    build: ./control-plane
    depends_on: [postgres, redis]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/tunnelplatform
      SPRING_REDIS_HOST: redis
    ports: ["8080:8080"]

  frps-vn01:
    image: fatedier/frps:latest
    volumes: ["./deploy/frps/frps-vn01.toml:/etc/frp/frps.toml"]
    ports: ["7000:7000", "20000-20100:20000-20100"]

  web:
    build: ./web
    ports: ["3000:3000"]

volumes:
  pgdata:
```

(Prometheus/Grafana/Loki added as a second compose file — kept out of the core loop until after Milestone 1, per §14.)

---

## 14. Development Instructions — Build Order

Follow v0.1's own instinct (don't build every protocol first) with concrete steps per milestone.

### Milestone 0 — Skeleton (½–1 week)
1. Monorepo scaffold per §12.
2. Control plane: Spring Boot app boots, connects to Postgres via Flyway-migrated schema (§9, users/agents/nodes/ports tables only).
3. `docker-compose up` brings up Postgres + Redis + control-plane health endpoint.

### Milestone 1 — The One Business Loop (v0.1 §45, this is the real MVP target)
1. **Auth:** email/password registration + login (Spring Security, session cookie via Redis).
2. **Node + port pool:** SUPERADMIN can register a node and seed an `allowPorts`-matching port range into `ports`.
3. **Resource request:** user submits a request; SUPERADMIN approves; allocation row created transactionally (§5).
4. **Agent registration:** agent generates keypair (§6), registers, receives device credential.
5. **Tunnel creation:** user creates a tunnel from their allocation; control plane writes desired-state version.
6. **Agent sync:** agent polls `/agent/v1/config`, sees new version, starts `frpc`.
7. **FRP plugin endpoint:** implement `Login`/`NewProxy` webhook in control plane; wire it into `frps.toml` (§9 — this is the step that makes the tunnel actually reachable, not just configured).
8. **Verify:** external TCP connection reaches the target through the node; dashboard shows `ACTIVE`.

Stop and stabilize here before adding anything else — this is the same checkpoint v0.1 recommended, now with the FRP-plugin step folded in as part of the *first* milestone rather than an afterthought, since without it step 6 doesn't actually produce a reachable tunnel.

### Milestone 2 — Lifecycle Hardening
1. Expiration/grace-period jobs (§10) with ShedLock.
2. Heartbeat + stale-agent detection (60s threshold).
3. Revocation (agent, credential, tunnel) and propagation via the polling interval (§4).
4. Audit logging on every mutating action.

### Milestone 3 — Multi-Tunnel, Multi-Node
1. Multiple tunnels per agent.
2. Second node (`SG-01`), node selection + latency ping UX (v0.1 §11.4).
3. Usage metering (`usage_records`, sampled counters from `frpc`/`frps` traffic stats).
4. Bandwidth + connection-count QoS enforcement (`frpc` proxy-level `bandwidthLimit`).

### Milestone 4 — Product Polish
1. Full admin dashboard (nodes, ports, tunnels, requests, audit — v0.1 §27 nav).
2. User dashboard + "Create Tunnel" intent-based form (v0.1 §28).
3. Rate limiting (Bucket4j + Redis) on login/registration/request/tunnel-creation endpoints.
4. Prometheus/Grafana/Loki stood up as a second compose stack; Node Agent exports node health metrics.

### Milestone 5 — Phase 2 candidates (only after Milestone 1–4 are stable in production use)
- gRPC bidirectional channel (swap behind the `AgentTransport` interface from §4).
- HTTP/HTTPS domain routing via Caddy integration.
- macOS agent build.
- Billing/subscription (Stripe), free→paid conversion flows.

---

## 15. Security Checklist (concrete, additive to v0.1 §38)

- [ ] Session cookies: `httpOnly`, `Secure`, `SameSite=Lax`.
- [ ] Agent device credential: short-lived session token issued after keypair-based auth, not the long-term key itself sent on every request.
- [ ] `NewProxy` plugin hook re-validates ownership + allocation status on **every** proxy attempt, not just at `Login` — a revoked allocation must fail the next `NewProxy` call even if the `Login` session is still nominally valid.
- [ ] Partial unique index (§5) is the final guard against port double-allocation even if application logic has a bug.
- [ ] Rate limits on `/api/v1/auth/*`, `/agent/v1/register`, `/api/v1/resource-requests`, `/api/v1/nodes/*/ping` (Bucket4j + Redis, distributed across instances).
- [ ] Audit every SUPERADMIN action and every revocation, including source IP.
- [ ] Secrets (DB password, session signing key, Google OAuth client secret) via environment/secret store, never committed — not explicit in v0.1, worth stating outright.

---

## 16. Open Decisions Left for Implementation

These were correctly left open in v0.1 and are still open — flagging rather than pretending to resolve them:

- Exact free-tier limits (device count, traffic caps) — product decision, not architectural.
- Degraded behavior during a Postgres outage (§35.4 in v0.1) — needs a concrete "fail closed except X" list once real failure-mode testing starts.
- Whether the Node Agent (gateway-side) is a genuinely separate Go binary or a mode flag on the same binary as the user agent — recommend separate binary (different privilege/deployment model) but either is workable; decide at Milestone 1 implementation time, not before.
