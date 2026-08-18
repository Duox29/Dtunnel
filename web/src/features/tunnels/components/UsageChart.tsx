import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useTunnelUsageHistory } from "../hooks";
import { formatBytes } from "../../../lib/format";

/**
 * Daily traffic chart (detail.md §3.5: Recharts). Shows bytes in/out per day
 * from the usage_daily rollup, oldest→newest left→right.
 */
export function UsageChart({ tunnelId, days = 30 }: { tunnelId: string; days?: number }) {
  const history = useTunnelUsageHistory(tunnelId, days);
  if (!history.data) {
    return <p className="py-8 text-center text-sm text-ink-dim">Loading traffic…</p>;
  }
  const data = [...history.data.days].reverse().map((d) => ({
    ...d,
    label: d.day.slice(5), // MM-DD
  }));
  if (data.length === 0) {
    return <p className="py-8 text-center text-sm text-ink-dim">No traffic recorded yet.</p>;
  }
  return (
    <div className="h-56 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id="gIn" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#58a6ff" stopOpacity={0.35} />
              <stop offset="100%" stopColor="#58a6ff" stopOpacity={0} />
            </linearGradient>
            <linearGradient id="gOut" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#3fb950" stopOpacity={0.35} />
              <stop offset="100%" stopColor="#3fb950" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="#1d232c" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" tick={{ fill: "#8b949e", fontSize: 11 }} axisLine={false} tickLine={false} />
          <YAxis
            tick={{ fill: "#8b949e", fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            width={64}
            tickFormatter={(v: number) => formatBytes(v)}
          />
          <Tooltip
            contentStyle={{
              background: "#161b22",
              border: "1px solid #2b313a",
              borderRadius: 8,
              fontSize: 12,
            }}
            labelStyle={{ color: "#e6edf3" }}
            formatter={(value, name) => [formatBytes(Number(value)), name === "bytesIn" ? "In" : "Out"]}
          />
          <Area type="monotone" dataKey="bytesIn" stroke="#58a6ff" strokeWidth={2} fill="url(#gIn)" name="bytesIn" />
          <Area type="monotone" dataKey="bytesOut" stroke="#3fb950" strokeWidth={2} fill="url(#gOut)" name="bytesOut" />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
