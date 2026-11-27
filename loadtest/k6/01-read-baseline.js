import { check } from 'k6';
import { get, login } from './lib/api.js';
import { businessErrors, trends } from './lib/metrics.js';
import { readBaselineSummary } from './lib/summary.js';

const scenario = JSON.parse(open('../data/scenario-c.json'));
const EMAIL = __ENV.LOAD_EMAIL || scenario.email || 'test-admin@example.local';
const PASSWORD = __ENV.LOAD_PASSWORD;

export const options = {
  setupTimeout: '1m',
  scenarios: {
    read_baseline: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      stages: [
        { target: 50, duration: '10s' },
        { target: 500, duration: '10s' },
        { target: 500, duration: '40s' },
        { target: 0, duration: '10s' },
      ],
      gracefulStop: '10s',
      tags: { scenario: 'read_baseline' },
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<100', 'p(99)<300'],
    'http_req_duration{endpoint:schedules}': ['p(95)<150'],
    'http_req_duration{endpoint:appointment}': ['p(95)<100'],
    'http_req_duration{endpoint:my_appointments}': ['p(95)<100'],
    'http_req_duration{endpoint:notifications}': ['p(95)<100'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  if (!PASSWORD) throw new Error('缺少 LOAD_PASSWORD 环境变量');
  return { token: login(EMAIL, PASSWORD), ...scenario };
}

function hit(key, path, data, endpoint) {
  const res = get(path, data.token, endpoint);
  trends[key].add(res.timings.duration);
  if (!check(res, {
    'http 200': (r) => r.status === 200,
    'business code 0': (r) => {
      try {
        return r.json('code') === 0;
      } catch (e) {
        return false;
      }
    },
  })) {
    businessErrors.add(1);
  }
}

export default function (data) {
  const r = Math.random();
  if (r < 0.30) {
    hit('schedules', `/doctors/${data.doctorId}/schedules`, data, 'schedules');
  } else if (r < 0.50) {
    hit('hospitals', '/hospitals', data, 'hospitals');
  } else if (r < 0.65) {
    hit('departments', `/hospitals/${data.hospitalId}/departments`, data, 'departments');
  } else if (r < 0.78) {
    hit('doctors', `/departments/${data.departmentId}/doctors`, data, 'doctors');
  } else if (r < 0.88) {
    hit('appointment', `/appointments/${data.appointmentId}`, data, 'appointment');
  } else if (r < 0.96) {
    hit('myAppointments', '/appointments/my', data, 'my_appointments');
  } else {
    hit('notifications', '/notifications', data, 'notifications');
  }
}

export function handleSummary(data) {
  return readBaselineSummary(data);
}
