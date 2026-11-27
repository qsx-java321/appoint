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

// 预约写接口明确预期的状态码；5xx 仍由自定义 booking_5xx 统计。
export const bookingResponseCallback = http.expectedStatuses(200, 201, 202, 409, 503);

export function issueToken(token) {
  return http.post(`${BASE_URL}/appointments/token`, null, {
    headers: authHeaders(token),
    tags: { endpoint: 'booking_token' },
    responseCallback: bookingResponseCallback,
  });
}

export function createAppointment(token, idempotencyKey, body) {
  return http.post(`${BASE_URL}/appointments`, JSON.stringify(body), {
    headers: {
      ...authHeaders(token),
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    tags: { endpoint: 'booking_create' },
    responseCallback: bookingResponseCallback,
  });
}

export function payAppointment(token, appointmentId) {
  return http.post(`${BASE_URL}/appointments/${appointmentId}/pay`, null, {
    headers: authHeaders(token),
    tags: { endpoint: 'booking_pay', name: 'POST /appointments/{id}/pay' },
    responseCallback: bookingResponseCallback,
  });
}

export function getAppointment(token, appointmentId) {
  return http.get(`${BASE_URL}/appointments/${appointmentId}`, {
    headers: authHeaders(token),
    tags: { endpoint: 'booking_detail', name: 'GET /appointments/{id}' },
    responseCallback: bookingResponseCallback,
  });
}
