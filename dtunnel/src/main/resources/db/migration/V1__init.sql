-- V1__init.sql — schema per detail.md §7 (Tunnel Management Platform)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

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
  public_key TEXT UNIQUE NOT NULL,     -- device identity, detail.md §6
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
-- detail.md §5: uniqueness only for live states so released ports are re-allocatable
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

-- ShedLock ledger for cluster-safe @Scheduled jobs (detail.md §10)
CREATE TABLE shedlock (
  name VARCHAR(64) NOT NULL,
  lock_until TIMESTAMP NOT NULL,
  locked_at TIMESTAMP NOT NULL,
  locked_by VARCHAR(255) NOT NULL,
  PRIMARY KEY (name)
);
