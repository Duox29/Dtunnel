import { useState } from "react";
import { useLogin, useRegister } from "../hooks";
import { Button, Card, Input } from "../../../components/ui";

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
    <div className="grid min-h-screen place-items-center p-4">
      <Card className="w-full max-w-sm">
        <form onSubmit={submit} className="space-y-4">
          <div>
            <h1 className="text-xl font-semibold">dtunnel</h1>
            <p className="text-sm text-ink-dim">Tunnel Management Platform</p>
          </div>
          <Input label="Email" type="email" value={email} onChange={setEmail} required />
          <Input label="Password" type="password" value={password} onChange={setPassword} required minLength={8} />
          {error && <p className="text-sm text-bad">{error}</p>}
          <Button type="submit" disabled={active.isPending} className="w-full">
            {mode === "login" ? "Sign in" : "Create account"}
          </Button>
          <button
            type="button"
            className="w-full text-center text-sm text-accent hover:underline"
            onClick={() => setMode(mode === "login" ? "register" : "login")}
          >
            {mode === "login" ? "Need an account? Register" : "Have an account? Sign in"}
          </button>
        </form>
      </Card>
    </div>
  );
}
