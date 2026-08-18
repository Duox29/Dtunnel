import type { ReactNode } from "react";

/** Friendly empty state with an optional call-to-action. */
export function EmptyState({
  icon,
  title,
  body,
  action,
}: {
  icon?: ReactNode;
  title: string;
  body?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-edge px-6 py-10 text-center">
      {icon && <div className="text-ink-faint">{icon}</div>}
      <p className="text-sm font-medium text-ink">{title}</p>
      {body && <p className="max-w-sm text-xs text-ink-dim">{body}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}
