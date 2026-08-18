# detail.md Compliance Audit

Section-by-section verification of the implementation against `detail.md` v0.2.
Audited 2026-08-18 after Milestones 0–4 completion; re-audited after Phase 2
(Node Agent + HTTP/HTTPS domain routing). Legend: ✅ implemented ·
🚧 partial · ⬜ deferred (with reason) · 🔀 deliberate deviation (with reason).

## §0 — The six v0.2 optimizations

| # | Optimization | Status | Evidence |
|---|---|---|---|
| 1 | FRP server-plugin hooks | ✅ | `FrpPluginController` (Login/NewProxy/Ping/CloseProxy), frps.toml httpPlugins |
| 2 | Transport behind interface | ✅ | Go `control/transport.go` (Transport interface + REST impl) |
| 3 | Virtual threads | ✅ | `spring.threads.virtual.enabled=true` |
| 4 | Redis required from MVP | ✅ | sessions, rate limits, agent tokens, stale window |
| 5 | FOR UPDATE SKIP LOCKED + partial index | ✅ | `PortRepository` native query; `uq_port_live` in V1 |
| 6 | ShedLock | ✅ | all @Scheduled jobs carry @SchedulerLock |

## §1–2 — Architecture, actors, state machines
✅ Control plane / data plane split; USER + SUPERADMIN only; all three state
machines (request, port, tunnel) implemented; 5-day warning → expiry → 3-day
grace → release implemented in `LifecycleJobs`.
✅ Gateway **Node Agent** (Phase 2) — §16's open decision resolved as a
**separate binary** (`duox-node-agent`): different privilege/deployment model
from the user agent. Reports health/capacity (load, memory, disk, frps proxy
count) via `POST /node/v1/heartbeat` authenticated by a per-node shared secret
issued at registration, rotatable by SUPERADMIN; stale nodes go OFFLINE
(`detectStaleNodes`, 120s threshold).

## §3 — Tech stack

| Item | Status | Note |
|---|---|---|
| Java / Spring Boot | 🔀 | **Java 25 + Boot 4.1.0** instead of 21/3.3 — user directive; virtual threads kept |
| Web/Security/JPA/Validation/Flyway | ✅ | Boot 4 module split handled (spring-boot-flyway, -jackson2, -session-data-redis) |
| MapStruct | 🔀 | not used — manual mapping in controllers; codebase small, no rework risk |
| ShedLock / Bucket4j / springdoc / Testcontainers | ✅ | ShedLock 7.8, Bucket4j 8.10.1 (+bucket4j-redis Lettuce), springdoc 3.1, TC 1.21.3 |
| Postgres 16 + JSONB + partial indexes | ✅ | V1__init.sql |
| Redis 7 | ✅ | |
| Go agent | ✅ | go 1.22; goreleaser builds both binaries for linux/windows/darwin amd64+arm64 (§3.4) |
| duox-node-agent | ✅ | Phase 2: separate binary (§16 decision), health/capacity heartbeat |
| React/TS/Vite | 🔀 | React **19** (spec: 18) — newer major, same APIs used |
| Tailwind + token-based UI | 🔀 | custom Tailwind v4 design tokens + hand-rolled ui kit instead of literal shadcn/ui — matches spec intent ("design tokens, not a heavy pre-styled kit") |
| TanStack Query + Router | ✅ | |
| Recharts | ✅ | usage charts (added in frontend polish) |
| Caddy (HTTP/HTTPS) | ✅ | Phase 2 (§3.6): Caddy edge container → frps vhostHTTPPort; domain-routed HTTP tunnels |
| PLG observability | ✅ | deploy/observability second compose stack; Prometheus scrapes /actuator/prometheus |
| OpenTelemetry traces | ⬜ | not in any milestone's build list; deferred |
| Docker Compose | ✅ | deploy/compose (+ e2e variant) |
| GitHub Actions CI | ✅ | .github/workflows/ci.yml (control-plane tests, agent vet+build, web build) |

## §4 — Agent channel
✅ REST + 15s heartbeat; revocation bounded to one round-trip (AgentTokenFilter
checks REVOKED on every request) — verified by Order 12 test.
✅ **gRPC transport shipped (Phase 2):** grpc-java 1.65 server on
`dtunnel.grpc.port` (default 9091). Bidirectional Control stream pushes
ConfigPush on every desired-state publish and Revoked immediately on revoke
(domain events → `GrpcPushService`); heartbeats share `AgentChannelService`
with REST so both transports apply identical semantics. Go agent runs a
reconnecting `grpcLoop` (`--grpc` flag); REST poll remains the backstop.
Verified by `GrpcChannelIntegrationTest` (register-over-gRPC, pushed config,
stream heartbeat ack, sub-second Revoked push) and live: both agents connected
with config pushes in control-plane logs.

## §5 — Port allocation
✅ `SELECT ... FOR UPDATE SKIP LOCKED` with candidate list + partial unique
index `uq_port_live` as the final double-allocation guard.

## §6 — Device identity / anti-abuse
✅ Ed25519 keypair persisted in `~/.config/duox-agent`; public key = durable
identity; short-lived Redis bearer token after key-based auth; IP rate limiting
on register (Bucket4j).
🚧 Email verification + release-then-recreate cooldown — no SMTP provider in
MVP; registration rate limits are the active anti-abuse control. Flagged for
production hardening.

## §7 — Schema
✅ All tables present (V1) + `nodes.frps_admin_url` (V2) + `usage_daily` (V3)
+ node agent columns (V4: `node_token`, `last_seen_at`) + HTTP tunnels (V5:
`tunnels.tunnel_type/domain/node_id`, partial unique `uq_tunnel_domain`,
`nodes.vhost_http_port`). Flyway-managed, `ddl-auto=validate`.

## §8 — API
✅ All listed endpoints implemented except:
⬜ `GET /api/v1/auth/google` — Google OAuth needs external client credentials;
schema already carries `google_subject`. Deferred until credentials exist.
Session-cookie auth with Redis store, rotated on login, SameSite=Lax. ✅

## §9 — FRP integration
✅ Full flow verified live: config poll → frpc start → Login hook → NewProxy
authorization (ownership + allocation ACTIVE + node/protocol/port match) →
ACTIVE. CloseProxy feeds status; Ping for liveness. allowPorts backstop in
frps.toml. Phase 2: NewProxy also polices HTTP proxies — the claimed
`custom_domains` must exactly match the tunnel's registered domain (Order 15
test; wrong-domain attempts denied).

## §10 — Background jobs
✅ All seven: expiration warnings, expirations, grace releases, stale agents,
**stale nodes** (Phase 2), **aggregateUsage** (UsageAggregateJob → usage_daily,
idempotent upsert, Order 13 test), reconcileDesiredVsObserved. All
ShedLock-guarded. Usage collector also polls frps `http` proxies (Phase 2).

## §11 — Reconciliation
✅ Desired-vs-observed with ERROR recovery; `configuration_versions` is the
single source of truth served by /agent/v1/config (divergence bug found and
fixed; publish on every desired-set change).

## §12 — Repository structure
✅ `dtunnel/` (api/domain/application/repo/security/config), `dtunnel-agent/`
(cmd/control/identity/runtime/frp), `web/` (features/components/routes),
`deploy/` (compose/frps/observability). `docs/` covered by README + AUDIT.md +
progression.md at repo root.

## §13 — Compose
✅ Core stack (postgres/redis/control-plane/frps) + **web** (multi-stage
Dockerfile, nginx SPA + deferred-DNS API proxy) + **caddy** (HTTP edge, Phase 2)
+ second observability file, exactly as §13/§14 prescribe. frps pinned
v0.71.0 (no `latest` tag upstream).

## §14 — Milestones
✅ 0–4 complete and verified live (see progression.md). Milestone 5 items are
spec-gated Phase 2 candidates.

## §15 — Security checklist
✅ All seven items: httpOnly/SameSite cookies; short-lived device token (never
the key); NewProxy re-validates ownership + allocation on every attempt;
partial unique index; Bucket4j rate limits on auth/register/requests/ping;
audit on every SUPERADMIN action + revocation **with source IP**
(AuditService.currentIp, X-Forwarded-For aware); secrets via env only.

## §16 — Open decisions
- Free-tier limits — product decision (billing deferred).
- Postgres-outage degraded behavior — needs failure-mode testing.
- ~~Node Agent binary shape~~ — **resolved**: separate `duox-node-agent` binary.

## Phase 2 additions (beyond Milestones 0–4)
- **Node Agent** (§1/§3.4/§16): `duox-node-agent` + `/node/v1/heartbeat` +
  token rotation + stale-node detection + capacity in node JSON.
- **HTTP/HTTPS domain routing** (§3.6): `POST /api/v1/tunnels/http` (domain
  normalization + platform-wide uniqueness), desired-state http proxies,
  plugin domain policing, frps `vhostHTTPPort`, Caddy edge, frontend
  dual-mode create form.
- **Web service in compose** (§13 gap) + goreleaser darwin (§3.4).

## Test evidence
16/16 integration tests green (Testcontainers Postgres 16 + Redis 7):
business loop, multi-tunnel, frps usage metering, revocation propagation,
usage aggregation idempotency, node-agent heartbeat + token rotation, HTTP
domain routing + plugin policing. Live E2E: real frpc 0.71 ↔ frps ↔ plugin,
TCP tunnels ACTIVE, **HTTP tunnel ACTIVE through Caddy edge** (Host-routed),
node agent ONLINE reporting capacity, ERROR recovery, rate limiting, PLG up.
