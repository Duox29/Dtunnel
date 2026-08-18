-- detail.md Milestone 3.3: usage metering reads per-proxy traffic counters
-- from each node's frps admin API (server is authoritative, §1).
ALTER TABLE nodes ADD COLUMN frps_admin_url VARCHAR(255);
