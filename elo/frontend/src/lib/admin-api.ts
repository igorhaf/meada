const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8200";

let adminToken = "";
if (typeof window !== "undefined") {
  adminToken = localStorage.getItem("elo_admin_token") || "";
}

export function setAdminToken(token: string) {
  adminToken = token;
  if (typeof window !== "undefined") localStorage.setItem("elo_admin_token", token);
}

export function getAdminToken() {
  if (typeof window !== "undefined") {
    adminToken = localStorage.getItem("elo_admin_token") || adminToken;
  }
  return adminToken;
}

async function adminFetch(path: string, opts: RequestInit = {}) {
  const token = getAdminToken();
  const headers: Record<string, string> = {
    "x-api-key": token,
    ...(opts.headers as Record<string, string> || {}),
  };
  if (!(opts.body instanceof FormData)) headers["Content-Type"] = "application/json";
  const res = await fetch(`${API}${path}`, { ...opts, headers });
  if (res.status === 403 || res.status === 401) throw new Error("Unauthorized");
  return res.json();
}

export const admin = {
  get: (path: string) => adminFetch(`/api/admin${path}`),
  post: (path: string, body?: unknown) => adminFetch(`/api/admin${path}`, { method: "POST", body: JSON.stringify(body) }),
  put: (path: string, body?: unknown) => adminFetch(`/api/admin${path}`, { method: "PUT", body: JSON.stringify(body) }),
  del: (path: string) => adminFetch(`/api/admin${path}`, { method: "DELETE" }),
};
