import type { HTMLAttributes } from "react";

export function Card({ className = "", ...props }: HTMLAttributes<HTMLElement>) {
  return (
    <section
      className={`rounded-xl border border-edge bg-panel p-4 shadow-sm ${className}`}
      {...props}
    />
  );
}

export function CardTitle({ children }: { children: React.ReactNode }) {
  return <h2 className="mb-3 text-base font-semibold">{children}</h2>;
}
