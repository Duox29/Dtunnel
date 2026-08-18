const OK = new Set(["ACTIVE", "ONLINE", "ALLOCATED", "APPROVED", "SUCCESS"]);
const BAD = new Set(["ERROR", "REVOKED", "REJECTED", "EXPIRED", "OFFLINE", "DISABLED"]);

export function StatusBadge({ status }: { status: string }) {
  const cls = OK.has(status)
    ? "bg-ok-bg text-ok"
    : BAD.has(status)
      ? "bg-bad-bg text-bad"
      : "bg-warn-bg text-warn";
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>
      {status}
    </span>
  );
}
