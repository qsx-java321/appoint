import axios, { type AxiosRequestConfig } from "axios";
import { clearSession, readSession } from "../lib/session";

export class ApiError extends Error {
  constructor(
    message: string,
    public status = 0,
    public code = 0,
  ) {
    super(message);
  }
  get uncertain() {
    return this.status === 0 || this.status >= 500;
  }
}
export const http = axios.create({ baseURL: "/api/v1", timeout: 15000 });
http.interceptors.request.use((config) => {
  const token = readSession()?.token;
  if (token && !config.url?.startsWith("/auth/"))
    config.headers.Authorization = `Bearer ${token}`;
  return config;
});
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isCancel(error)) return Promise.reject(error);
    const status = error.response?.status || 0;
    if (status === 401 && !error.config?.url?.startsWith("/auth/")) {
      const sent = String(error.config?.headers?.Authorization || "").replace(
        /^Bearer /,
        "",
      );
      if (sent) clearSession(sent);
      else if (!readSession()) clearSession();
    }
    return Promise.reject(
      new ApiError(
        error.response?.data?.message ||
          (status ? "请求失败，请重试。" : "网络连接失败，请检查连接后重试。"),
        status,
        error.response?.data?.code || 0,
      ),
    );
  },
);
export async function request<T>(
  url: string,
  config: AxiosRequestConfig = {},
): Promise<T> {
  const { data } = await http.request<{
    code: number;
    message: string;
    data: T;
  }>({ url, ...config });
  if (data.code !== 0) throw new ApiError(data.message, 200, data.code);
  return data.data;
}
export const message = (error: unknown) =>
  error instanceof Error ? error.message : "操作失败，请重试。";
