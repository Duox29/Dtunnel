import { apiFetch } from "../../lib/api-client";
import type { Me } from "./types";

export const authApi = {
  me: () => apiFetch<Me>("GET", "/api/v1/auth/me"),
  login: (email: string, password: string) =>
    apiFetch<Me>("POST", "/api/v1/auth/login", { email, password }),
  register: (email: string, password: string) =>
    apiFetch<Me>("POST", "/api/v1/auth/register", { email, password }),
  logout: () => apiFetch<{ status: string }>("POST", "/api/v1/auth/logout"),
};
