export interface Me {
  id: string;
  email: string;
  role: "USER" | "SUPERADMIN";
  plan: string;
}
