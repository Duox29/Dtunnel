import { useState } from "react";
import { useLogin, useRegister } from "../hooks";
import { Button, Input } from "../../../components/ui";

/**
 * Market-style auth screen: brand panel on the left (ngrok/Cloudflare
 * pattern), compact form on the right.
 */
export function AuthPage() {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const login = useLogin();
  const register = useRegister();
  const active = mode === "login" ? login : register;
  const error = active.error instanceof Error ? active.error.message : "";

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    active.mutate({ email, password });
  };

  return (
    <div className="flex min-h-screen">
      {/* brand panel */}
      <div className="hidden w-1/2 flex-col justify-between border-r border-edge bg-sidebar p-10 lg:flex">
        <div className="flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent-soft text-accent">
            <svg width="20" height="20" viewBox="0 0 16 16" fill="none" aria-hidden>
              <path d="M3 11V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v6M3 11h10M5.5 11v2M10.5 11v2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          </span>
          <span className="text-lg font-semibold tracking-tight">dtunnel</span>
        </div>
        <div>
          <h2 className="max-w-md text-3xl font-semibold leading-tight">
            Expose local services through managed, audited tunnels.
          </h2>
          <p className="mt-4 max-w-md text-sm leading-relaxed text-ink-dim">
            Request a public port, point the agent at your service, and go live.
            Every tunnel is authorized in real time, metered, and revocable.
          </p>
        </div>
        <p className="text-xs text-ink-faint">Tunnel Management Platform</p>
      </div>

      {/* form panel */}
      <div className="flex flex-1 items-center justify-center p-6">
        <div className="w-full max-w-sm">
          <div className="mb-6 lg:hidden">
            <h1 className="text-xl font-semibold">dtunnel</h1>
            <p className="text-sm text-ink-dim">Tunnel Management Platform</p>
          </div>
          <h1 className="text-xl font-semibold">
            {mode === "login" ? "Sign in" : "Create your account"}
          </h1>
          <p className="mt-1 text-sm text-ink-dim">
            {mode === "login" ? "Welcome back." : "Register to request tunnel resources."}
          </p>
          <form onSubmit={submit} className="mt-6 space-y-4">
            <Input label="Email" type="email" value={email} onChange={setEmail} required placeholder="you@example.com" />
            <Input label="Password" type="password" value={password} onChange={setPassword} required minLength={8} placeholder="••••••••" />
            {error && (
              <p className="rounded-md border border-bad/40 bg-bad-bg px-3 py-2 text-sm text-bad">{error}</p>
            )}
            <Button type="submit" disabled={active.isPending} className="w-full justify-center py-2">
              {active.isPending ? "Please wait…" : mode === "login" ? "Sign in" : "Create account"}
            </Button>
          </form>
          <p className="mt-5 text-center text-sm text-ink-dim">
            {mode === "login" ? "Need an account? " : "Have an account? "}
            <button
              type="button"
              className="font-medium text-accent hover:underline"
              onClick={() => setMode(mode === "login" ? "register" : "login")}
            >
              {mode === "login" ? "Register" : "Sign in"}
            </button>
          </p>
        </div>
      </div>
    </div>
  );
}
