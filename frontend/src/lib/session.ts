import type { Login, User } from "../api/types";

export interface Session {
  token: string;
  expiresAt: number;
  user: User;
}
const key = "appoint.session.v1";
export function readSession(): Session | null {
  try {
    const value: Session = JSON.parse(sessionStorage.getItem(key) || "null");
    if (
      value &&
      typeof value.token === "string" &&
      value.expiresAt > Date.now() &&
      typeof value.user?.id === "string" &&
      Array.isArray(value.user.roles)
    )
      return value;
  } catch {
    /* 存储不可用或旧会话格式无效，按未登录处理。 */
  }
  return null;
}
export function writeSession(login: Login): Session {
  const session = {
    token: login.accessToken,
    expiresAt: Date.now() + login.expiresIn * 1000,
    user: login.user,
  };
  sessionStorage.setItem(key, JSON.stringify(session));
  return session;
}
export function clearSession(expectedToken?: string) {
  if (expectedToken && readSession()?.token !== expectedToken) return;
  sessionStorage.removeItem(key);
  sessionStorage.removeItem("appoint.submission.v1");
  window.dispatchEvent(new Event("appoint:logout"));
}
export function safeReturn(value: unknown): string {
  if (
    typeof value !== "string" ||
    !/^\/(booking|appointments|patients|account|doctor|admin)(\/|\?|$)/.test(
      value,
    )
  )
    return "/booking";
  if (/[\\\u0000-\u0020]/.test(value)) return "/booking";
  return value;
}
