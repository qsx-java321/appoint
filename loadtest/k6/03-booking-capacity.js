import { login } from './lib/api.js';
import { bookingFlow, pairFor, tierTags } from './lib/booking.js';
import * as m from './lib/metrics-booking.js';
import { bookingSummary } from './lib/summary-booking.js';

const data = JSON.parse(open('../data/scenario-a.json'));
const EMAIL = __ENV.LOAD_EMAIL || data.email || 'test-admin@example.local';
const PASSWORD = __ENV.LOAD_PASSWORD;
const TIERS = (__ENV.TIERS || '10,30,50,80').split(',').map((x) => Number(x.trim())).filter((x) => x > 0);

// 固定 OFFSET，保证不同 scenario 的 (patient, schedule) 不重复。
const OFFSETS = {
  10: 0,
  30: 1000,
  50: 2000,
  80: 4000,
  100: 7000,
  120: 10000,
  160: 14000,
};

// 乘数 104729 与 300*144=43200 互质，保证 pair 置换是双射。

for (const tier of TIERS) {
  if (OFFSETS[tier] === undefined) {
    throw new Error(`不支持的 TIER ${tier}，可用值: ${Object.keys(OFFSETS).join(',')}`);
  }
}

const scenarios = {};
const thresholds = {
  booking_schedule_full: ['count==0'],
  booking_4204: ['count==0'],
  booking_4202: ['count==0'],
  booking_unexpected: ['count==0'],
  booking_ok: ['rate>0.99'],
  booking_5xx: ['rate<0.01'],
  http_req_failed: ['rate<0.01'],
  'http_req_duration{expected_response:true}': ['p(95)<300', 'p(99)<800'],
};

TIERS.forEach((tier, index) => {
  const tag = `r${tier}`;
  scenarios[`r${tier}`] = {
    executor: 'constant-arrival-rate',
    rate: tier,
    timeUnit: '1s',
    duration: '30s',
    startTime: `${index * 35}s`,
    preAllocatedVUs: Math.max(100, tier * 4),
    maxVUs: Math.max(200, tier * 8),
    gracefulStop: '5s',
    tags: { tier: tag },
    env: { TIER: tag, OFFSET: String(OFFSETS[tier]) },
  };
  thresholds[`http_req_duration{tier:${tag}}`] = ['p(95)<300'];
  thresholds[`booking_ok{tier:${tag}}`] = ['rate>0.99'];
  thresholds[`booking_5xx{tier:${tag}}`] = ['rate<0.01'];
  thresholds[`booking_attempts{tier:${tag}}`] = ['count>=1'];
  thresholds[`booking_success{tier:${tag}}`] = ['count>=1'];
});

export const options = {
  setupTimeout: '1m',
  scenarios,
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  if (!PASSWORD) throw new Error('缺少 LOAD_PASSWORD 环境变量');
  return { token: login(EMAIL, PASSWORD) };
}

export default function (runtime) {
  const offset = Number(__ENV.OFFSET);
  const pair = pairFor(data, offset);
  if (!pair) {
    const tags = tierTags();
    m.bookingAttempts.add(1, tags);
    m.bookingUnexpected.add(1, tags);
    m.bookingOk.add(false, tags);
    return;
  }
  bookingFlow(runtime.token, pair.patientId, pair.slot);
}

export function handleSummary(summary) {
  return bookingSummary(summary, 'a');
}
