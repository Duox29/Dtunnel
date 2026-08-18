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
| docker-compose up: Postgres + Redis + control-plane health | 🚧 | compose file pending |

## Milestone 1 — The One Business Loop (detail.md §14)

| Step | Status | Notes |
|---|---|---|
| Domain entities + enums (users, agents, nodes, ports, requests, allocations, tunnels, config versions, usage, audits) | ✅ | jakarta.persistence, state-machine enums per §2 |
| JPA repositories incl. FOR UPDATE SKIP LOCKED port lock (§5) | ✅ | |
| Security: session-cookie /api/v1, bearer device-token /agent/v1, AgentTokenFilter, Redis token store (§8, §6, §15) | ✅ | |
| Services: Audit, Auth, Node+port seeding, PortAllocation (§5), ResourceRequest lifecycle (§2), Agent registration (§6), DesiredState/config versions, Tunnel CRUD | ✅ | |
| Controllers: /api/v1 auth, nodes, ports, resource-requests, tunnels, agents, audits | ⬜ | |
| Agent API: /agent/v1 register, config/version, config, heartbeat | ⬜ | |
| FRP server-plugin webhook /agent/v1/frp-plugin (Login/NewProxy/Ping/CloseProxy, §9) | ⬜ | |
| Go agent (identity Ed25519, REST transport, config sync, frpc adapter, heartbeat) | ⬜ | dtunnel-agent/ |
| E2E verify: register→approve→allocate→agent sync→frpc→ACTIVE | ⬜ | |

## Milestone 2 — Lifecycle Hardening

| Step | Status | Notes |
|---|---|---|
| Expiration/grace jobs with ShedLock (§10) | ⬜ | |
| Stale-agent detection (60s, §10) | ⬜ | |
| Revocation propagation (§4) | ⬜ partial | status check per request already in AgentTokenFilter |
| Audit logging on mutating actions (§15) | ✅ | AuditService wired into services |

## Milestone 3+ — deferred

Multi-tunnel/multi-node, usage metering, QoS, web UI polish, rate limiting (Bucket4j), PLG stack, gRPC transport — not started.

## Decisions log

- **Java 25 + Spring Boot 4.1.0** instead of spec's Java 21 + Boot 3.3 (user directive; virtual threads still enabled).
- ShedLock 7.x / springdoc 3.x (Boot 4-compatible lines).
- JDK installed at `~/.jdks/jdk-25.0.4+7` (no sudo for apt).
- Git repo at ProjectDtunnel root covers `dtunnel/` and `dtunnel-agent/`.
