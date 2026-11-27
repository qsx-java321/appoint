import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080/api/v1';

export function login(email, password) {
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'login' },
  });
  let payload;
  try {
    payload = res.json();
  } catch (e) {
    throw new Error(`登录响应不是 JSON: HTTP ${res.status} ${res.body}`);
  }
  if (res.status !== 200 || payload.code !== 0 || !payload.data || !payload.data.accessToken) {
    throw new Error(`登录失败: HTTP ${res.status} code=${payload.code} message=${payload.message}`);
  }
  return payload.data.accessToken;
}

export function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

export function get(path, token, endpoint) {
  return http.get(`${BASE_URL}${path}`, {
    headers: authHeaders(token),
    tags: { endpoint },
  });
}

export function checkRead(res) {
  return check(res, {
    'http 200': (r) => r.status === 200,
    'business code 0': (r) => {
      try {
        return r.json('code') === 0;
      } catch (e) {
        return false;
      }
    },
  });
}
