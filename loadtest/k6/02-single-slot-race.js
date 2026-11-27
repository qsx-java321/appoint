import { login } from './lib/api.js';
import {
  issueBookingToken,
  createWithToken,
  tierTags,
  waitUntil,
} from './lib/booking.js';
import * as m from './lib/metrics-booking.js';
import { bookingSummary } from './lib/summary-booking.js';

const data = JSON.parse(open('../data/scenario-b.json'));
const EMAIL = __ENV.LOAD_EMAIL || data.email || 'test-admin@example.local';
const PASSWORD = __ENV.LOAD_PASSWORD;

export const options = {
  setupTimeout: '1m',
  scenarios: {
    single_slot_race: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 1,
      maxDuration: '30s',
      gracefulStop: '5s',
      tags: { scenario: 'single_slot_race' },
    },
  },
  thresholds: {
    booking_attempts: ['count==100'],
    booking_success: ['count==1'],
    booking_schedule_full: ['count==99'],
    booking_4204: ['count==0'],
    booking_4202: ['count==0'],
    booking_4102: ['count==0'],
    booking_4103: ['count==0'],
    booking_4104: ['count==0'],
    booking_unexpected: ['count==0'],
    booking_outcome_ok: ['rate>0.99'],
    booking_5xx: ['rate<0.01'],
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  if (!PASSWORD) throw new Error('缺少 LOAD_PASSWORD 环境变量');
  return {
    token: login(EMAIL, PASSWORD),
    startAt: Date.now() + 5000,
  };
}

export default function (runtime) {
  const tags = tierTags();
  m.bookingAttempts.add(1, tags);
  const start = Date.now();
  const slot = data.slots[0];
  const patientId = data.patients[(__VU - 1) % data.patients.length];

  const issued = issueBookingToken(runtime.token);
  if (!issued.ok) {
    m.bookingUnexpected.add(1, tags);
    m.bookingOutcomeOk.add(false, tags);
    m.bookingIterationMs.add(Date.now() - start, tags);
    return;
  }

  // 所有 VU 在同一个业务时刻发起创建，尽量制造真实竞争。
  waitUntil(runtime.startAt);

  const created = createWithToken(runtime.token, issued.token, {
    doctorId: slot.doctorId,
    appointmentDate: slot.appointmentDate,
    sessionType: slot.sessionType,
    patientId,
  });

  if (created.ok) {
    m.bookingSuccess.add(1, tags);
    m.bookingOutcomeOk.add(true, tags);
  } else if (created.reason === 'full') {
    m.bookingOutcomeOk.add(true, tags);
  } else {
    m.bookingOutcomeOk.add(false, tags);
  }
  m.bookingIterationMs.add(Date.now() - start, tags);
}

export function handleSummary(summary) {
  return bookingSummary(summary, 'b');
}
