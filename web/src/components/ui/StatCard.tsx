import type { ReactNode } from "react";

/** Market-style KPI tile (Cloudflare/ngrok dashboard pattern). */
export function StatCard({
  label,
  value,
  hint,
  tone = "default",
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: "default" | "ok" | "bad" | "warn" | "accent";
}) {
  const toneCls =
    tone === "ok" ? "text-ok"
    : tone === "bad" ? "text-bad"
    : tone === "warn" ? "text-warn"
    : tone === "accent" ? "text-accent"
    : "text-ink";
  return (
    <div className="rounded-lg border border-edge bg-panel p-4">
      <p className="text-xs font-medium uppercase tracking-wider text-ink-dim">{label}</p>
      <p className={`mt-1.5 text-2xl font-semibold tabular-nums ${toneCls}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-ink-faint">{hint}</p>}
    </div>
  );
}
