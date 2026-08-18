-- detail.md §1/§3.4/§16: Gateway Node Agent support.
-- The node agent authenticates with a per-node shared secret (node_token) and
-- reports health/capacity on a heartbeat; the control plane marks the node
-- OFFLINE when heartbeats stop (mirrors stale-agent detection, §10).
ALTER TABLE nodes ADD COLUMN node_token VARCHAR(128);
ALTER TABLE nodes ADD COLUMN last_seen_at TIMESTAMPTZ;
