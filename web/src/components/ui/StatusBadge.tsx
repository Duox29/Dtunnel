const OK = new Set(["ACTIVE", "ONLINE", "ALLOCATED", "APPROVED", "SUCCESS"]);
const BAD = new Set(["ERROR", "REVOKED", "REJECTED", "EXPIRED", "OFFLINE", "DISABLED"]);
const TRANSIENT = new Set(["STARTING", "STOPPING", "PENDING", "SUBMITTED", "CONFIGURED", "CREATED", "EXPIRING", "EXPIRED_PENDING_RELEASE", "RESERVED"]);

/** Status pill with a leading dot — the pattern used by ngrok/Cloudflare dashboards. */
export function StatusBadge({ status }: { status: string }) {
  const tone = OK.has(status) ? "ok" : BAD.has(status) ? "bad" : "warn";
  const cls =
    tone === "ok" ? "bg-ok-bg text-ok"
    : tone === "bad" ? "bg-bad-bg text-bad"
    : "bg-warn-bg text-warn";
  const dot =
    tone === "ok" ? "bg-ok"
    : tone === "bad" ? "bg-bad"
    : "bg-warn";
  const pulse = TRANSIENT.has(status) ? "animate-pulse" : "";
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${dot} ${pulse}`} />
      {status}
    </span>
  );
}
