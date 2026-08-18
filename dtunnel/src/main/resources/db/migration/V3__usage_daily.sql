-- detail.md §10 aggregateUsage(): roll hourly usage_records into daily totals.
-- Idempotent: the job recomputes each day's sum and upserts.
CREATE TABLE usage_daily (
  tunnel_id UUID NOT NULL REFERENCES tunnels(id) ON DELETE CASCADE,
  day DATE NOT NULL,
  bytes_in BIGINT NOT NULL DEFAULT 0,
  bytes_out BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (tunnel_id, day)
);
