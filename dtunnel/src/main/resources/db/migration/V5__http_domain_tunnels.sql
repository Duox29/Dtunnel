-- detail.md §3.6: HTTP/HTTPS domain routing via Caddy (Phase 2).
-- PORT tunnels (tcp/udp) keep a dedicated allocated port. HTTP tunnels are
-- domain-routed over the node's shared frps vhost HTTP port, so they carry a
-- domain instead of a port allocation.
ALTER TABLE tunnels ALTER COLUMN port_allocation_id DROP NOT NULL;
ALTER TABLE tunnels ADD COLUMN node_id UUID REFERENCES nodes(id);
ALTER TABLE tunnels ADD COLUMN domain VARCHAR(253);
-- PORT (tcp/udp via allocation) | HTTP (domain-routed). Existing rows are PORT.
ALTER TABLE tunnels ADD COLUMN tunnel_type VARCHAR(16) NOT NULL DEFAULT 'PORT';
-- a domain may only be claimed by one live tunnel
CREATE UNIQUE INDEX uq_tunnel_domain ON tunnels(domain) WHERE domain IS NOT NULL;
-- the shared frps vhost HTTP port on the node (Caddy forwards here)
ALTER TABLE nodes ADD COLUMN vhost_http_port INT;
