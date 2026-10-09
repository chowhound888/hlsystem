import axios from "axios";
import { ElMessage } from "element-plus";
export const client = axios.create({
  baseURL: "/api/v1",
  withCredentials: true,
  timeout: 30000,
});
let csrfToken = "";
export async function refreshCsrf() {
  const r = await client.get("/auth/csrf");
  csrfToken = r.data.data.token;
}
client.interceptors.request.use((config) => {
  if (config.method !== "get") config.headers["X-XSRF-TOKEN"] = csrfToken;
  return config;
});
client.interceptors.response.use(
  (r) => r,
  (e) => {
    ElMessage.error(e.response?.data?.message ?? "无法连接服务，请稍后重试");
    return Promise.reject(e);
  },
);
export async function get<T = any>(url: string, params?: object): Promise<T> {
  return (await client.get(url, { params })).data.data;
}
export async function post<T = any>(
  url: string,
  data?: object,
  key?: string,
): Promise<T> {
  return (
    await client.post(url, data, {
      headers: key ? { "Idempotency-Key": key } : undefined,
    })
  ).data.data;
}
export async function put<T = any>(url: string, data: object): Promise<T> {
  return (await client.put(url, data)).data.data;
}
