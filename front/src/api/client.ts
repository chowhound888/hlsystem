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

// 请求拦截器 - 添加 CSRF Token
client.interceptors.request.use((config) => {
  if (config.method !== "get") {
    config.headers["X-XSRF-TOKEN"] = csrfToken;
  }
  return config;
});

// 响应拦截器 - 静默模式（不自动显示错误）
let silentMode = false;

export function setSilentMode(silent: boolean) {
  silentMode = silent;
}

client.interceptors.response.use(
  (r) => r,
  (e) => {
    if (!silentMode) {
      // 默认错误提示（简化版，详细处理由调用方决定）
      const message = e.response?.data?.message ?? "无法连接服务，请稍后重试";
      ElMessage.error(message);
    }
    return Promise.reject(e);
  }
);

/**
 * 带错误处理的 GET 请求
 */
export async function get<T = any>(url: string, params?: object): Promise<T> {
  try {
    const response = await client.get(url, { params });
    return response.data.data;
  } catch (error) {
    throw error;
  }
}

/**
 * 带错误处理的 POST 请求
 */
export async function post<T = any>(
  url: string,
  data?: object,
  key?: string
): Promise<T> {
  try {
    const response = await client.post(url, data, {
      headers: key ? { "Idempotency-Key": key } : undefined,
    });
    return response.data.data;
  } catch (error) {
    throw error;
  }
}

/**
 * 带错误处理的 PUT 请求
 */
export async function put<T = any>(url: string, data: object): Promise<T> {
  try {
    const response = await client.put(url, data);
    return response.data.data;
  } catch (error) {
    throw error;
  }
}

/**
 * 静默执行请求（不显示默认错误提示）
 * 用于需要自定义错误处理的场景
 */
export async function silentRequest<T>(fn: () => Promise<T>): Promise<T> {
  const prevSilent = silentMode;
  silentMode = true;
  try {
    return await fn();
  } finally {
    silentMode = prevSilent;
  }
}
