# Dtunnel — Build Progression

Tracks implementation of `detail.md` (Tunnel Management Platform spec v0.2).
Update after every successful step; each entry maps to a git commit.

**Legend:** ✅ done · 🚧 in progress · ⬜ not started

## Milestone 0 — Skeleton (detail.md §14)

| Step | Status | Notes |
|---|---|---|
| Monorepo scaffold (dtunnel control plane + dtunnel-agent) | ✅ | git repo covers both dirs |
| Toolchain: JDK 25 (Temurin, ~/.jdks), Go 1.22, Node 24, Docker | ✅ | user chose Java 25 over spec's 21 |
| pom.xml: Spring Boot 4.1.0, Java 25, web/security/jpa/redis/validation/actuator/flyway/shedlock/springdoc/testcontainers | ✅ | |
| Flyway V1__init.sql: full schema per §7 + shedlock table | ✅ | |
| application.properties: virtual threads, Postgres, Redis sessions, cookie hardening, dtunnel.* knobs | ✅ | |
| docker-compose up: Postgres + Redis + control-plane health | ✅ | verified via E2E run (compose.e2e variant + full stack file) |

## Milestone 1 — The One Business Loop (detail.md §14)

| Step | Status | Notes |
|---|---|---|
| Domain entities + enums (users, agents, nodes, ports, requests, allocations, tunnels, config versions, usage, audits) | ✅ | jakarta.persistence, state-machine enums per §2 |
| JPA repositories incl. FOR UPDATE SKIP LOCKED port lock (§5) | ✅ | |
| Security: session-cookie /api/v1, bearer device-token /agent/v1, AgentTokenFilter, Redis token store (§8, §6, §15) | ✅ | |
| Services: Audit, Auth, Node+port seeding, PortAllocation (§5), ResourceRequest lifecycle (§2), Agent registration (§6), DesiredState/config versions, Tunnel CRUD | ✅ | |
| Controllers: /api/v1 auth, nodes, ports, resource-requests, tunnels, agents, audits | ✅ | |
| Agent API: /agent/v1 register, config/version, config, heartbeat | ✅ | |
| FRP server-plugin webhook /agent/v1/frp-plugin (Login/NewProxy/Ping/CloseProxy, §9) | ✅ | identity via frpc user field `<agentId>.<token>`; token via query param (frps plugins can't send headers, verified frp 0.63) |
| Go agent (identity Ed25519, REST transport, config sync, frpc adapter, heartbeat) | ✅ | dtunnel-agent/, builds clean |
| E2E verify: register→approve→allocate→agent sync→frpc→ACTIVE | ✅ | real frpc 0.71 ↔ frps container ↔ plugin webhook; `DTUNNEL-E2E-OK` received through :20005 |

## Milestone 2 — Lifecycle Hardening

| Step | Status | Notes |
|---|---|---|
| Expiration/grace jobs with ShedLock (§10) | ✅ | LifecycleJobs: warnings/expiry/grace/stale/reconcile |
| Stale-agent detection (60s, §10) | ✅ | |
| Revocation propagation (§4) | ✅ | AgentTokenFilter rejects REVOKED on every request (401); /config FORBIDDEN; FRP plugin denies Login/NewProxy; **terminal by design** (device must re-register); Order 12 test verifies all four paths |
| Audit logging on mutating actions (§15) | ✅ | AuditService wired into services |

## Milestone 3 — Multi-Tunnel, Multi-Node

| Step | Status | Notes |
|---|---|---|
| Multiple tunnels per agent | ✅ | test: 2 proxies in desired state, both ACTIVE (Order 10); live: 2 tunnels ACTIVE on one agent |
| Usage metering (usage_records) | ✅ | **server-side** per §1: UsageCollectorJob polls each node's frps admin API (`/api/proxy/{type}` → todayTrafficIn/Out), records deltas into usage_records; `GET /api/v1/tunnels/{id}/usage`; nodes.frps_admin_url (V2) + `PATCH /api/v1/nodes/{id}`; test uses stub frps endpoint (Order 11); live: 3000B in / 42B out collected |
| Second node + selection UX | ✅ | SG-01 registered live; RequestsPanel already has node selector |
| QoS: bandwidthLimit | ✅ | Mbps→KB unit fix (frp unit = bytes/s); 5Mbps renders `transport.bandwidthLimit = "610KB"`; verified live |
| QoS: max connections | ⬜ | frp has no per-proxy max-connections for TCP; would need a different mechanism — deferred |
| ERROR recovery (§11) | ✅ | reconciler re-arms ERROR→STARTING when agent back ONLINE + republishes; verified live (kill agent → ERROR → restart → ACTIVE) |
| Desired-state sync (§11) | ✅ | **critical fix**: /config returns STORED configuration_versions payload (single source of truth) so version always matches payload; publish on every desired-set change (CloseProxy ERROR, reconciler ERROR flag) |

**Usage architecture note:** frpc v0.71 exposes NO client-side traffic API
(`/api/status` has no counters; no client Prometheus metrics). frps DOES expose
per-proxy daily counters via its admin API. Since §1 says the server is
authoritative, collection lives in the control plane (UsageCollectorJob,
ShedLock-guarded, 60s poll, delta-based with day-rollover handling).
Agent-side sampling was built then removed as dead code.

**Desired-state sync note (§11):** `configuration_versions` is the single source
of truth. `/agent/v1/config` returns the STORED payload (not a live rebuild) so
the version number always matches what the agent applies. Every transition that
changes the desired set (create/start/stop/delete, CloseProxy→ERROR, reconciler
ERROR flag, reconciler ERROR→STARTING recovery) calls `publish()` to bump the
version. A live rebuild in /config could diverge from the stored version and
strand the agent (bug found + fixed during E2E).

## Milestone 4 — Product Polish

| Step | Status | Notes |
|---|---|---|
| Full admin dashboard (nodes, ports, tunnels, requests, audit) | ✅ | routes/pages.tsx: Overview/Tunnels/Requests/Agents/Admin; all panels feature-based |
| User dashboard + intent-based "Create Tunnel" form (v0.1 §28) | ✅ | service presets (SSH/HTTP/HTTPS/RDP/MySQL/Postgres/custom) pre-fill target port + name; labeled fields; live exposure summary |
| Rate limiting (Bucket4j + Redis, §15) | ✅ | Bucket4j 8.10.1 + bucket4j-redis Lettuce backend (Lettuce 7 credentials API); auth 10/min/IP, agent-register 5/min/IP, resource-requests 30/h/user, node-ping 60/min/user; 429 + Retry-After; filter ordered -200 (before security chain); **verified live: attempts 1-10 → 401, 11-12 → 429** |
| Prometheus/Grafana/Loki second compose stack | ✅ | deploy/observability/ (docker-compose.observability.yml joins compose_default network); scrapes control-plane /actuator/prometheus (permitted in SecurityConfig); Grafana datasources provisioned; **verified live: up{job="dtunnel-control-plane"}=1, Grafana healthy, Loki ready** |
| Node Agent exports node health metrics | ⬜ | Node Agent binary itself is an open decision (§16) — deferred with it |

**Observability gotcha:** Prometheus/Grafana run as non-root container users;
mounted config files must be world-readable (644), not the default 600.

## Post-Milestone-4 — Compliance audit + frontend polish

| Step | Status | Notes |
|---|---|---|
| Section-by-section detail.md audit | ✅ | **AUDIT.md** — every §0–§16 verified; deviations (Java 25/Boot 4, React 19, custom Tailwind tokens vs literal shadcn) + deferrals documented |
| aggregateUsage() (§10) | ✅ | was missing → UsageAggregateJob (ShedLock, hourly cron, idempotent upsert) + usage_daily table (V3) + GET /tunnels/{id}/usage/history; Order 13 test |
| GitHub Actions CI (§3.8) | ✅ | .github/workflows/ci.yml — control-plane tests, agent vet+build, web build |
| goreleaser (§3.4) | ✅ | dtunnel-agent/.goreleaser.yaml — linux/windows amd64+arm64 |
| Frontend polish (market-style) | ✅ | ngrok/Cloudflare/Tailscale patterns: sidebar shell + icons, KPI stat cards, Recharts traffic chart (§3.5), status pills w/ dot+pulse, relative time, empty states, toasts, confirm dialogs, copy buttons, split-panel login; verified via Vite dev server against live API |

## Milestone 5+ — deferred (Phase 2 candidates)

gRPC transport, HTTP/HTTPS domain routing (Caddy), macOS agent, billing/Stripe — per §14, only after Milestones 1–4 are stable in production use.

## Boot 4 modularization gotchas (hit during build)

Spring Boot 4.1 split autoconfiguration into modules; the plain starters no longer pull them in:

| Need | Module added |
|---|---|
| Flyway migration on boot | `spring-boot-flyway` |
| Jackson 2 ObjectMapper bean | `spring-boot-jackson2` (not `spring-boot-jackson` = Jackson 3) |
| Spring Session Redis autoconfig | `spring-boot-session-data-redis` |
| MockMvc test slice | `spring-boot-webmvc-test` (package `org.springframework.boot.webmvc.test.autoconfigure`) |

Other environment fixes:
- Docker Engine 29 dropped API <1.44; docker-java defaults to 1.32 → surefire passes `-Dapi.version=1.44` (pom property `docker.api.version`).
- frps server plugins cannot send custom headers (frp 0.63) → plugin shared secret travels as `?token=` query param.

## Decisions log

- **Java 25 + Spring Boot 4.1.0** instead of spec's Java 21 + Boot 3.3 (user directive; virtual threads still enabled).
- ShedLock 7.x / springdoc 3.x (Boot 4-compatible lines).
- JDK installed at `~/.jdks/jdk-25.0.4+7` (no sudo for apt).
- Git repo at ProjectDtunnel root covers `dtunnel/` and `dtunnel-agent/`.
- frps image pinned to `fatedier/frps:v0.71.0` (no `latest` tag exists).
- FRP plugin protocol (from frp source pkg/plugin/server/types.go): Login has flat `user` string; NewProxy/Ping/CloseProxy wrap identity in UserInfo object; response key is `reject_reason`.
- Web: industrial-grade layout per §12 — features/ by domain (types+api+hooks+components), components/ui, routes/ with TanStack Router guards + TanStack Query, Tailwind v4.