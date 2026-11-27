import exec from 'k6/execution';
import { sleep } from 'k6';
import {
  createAppointment,
  getAppointment,
  issueToken,
  payAppointment,
} from './api.js';
import * as m from './metrics-booking.js';

export function tierTags() {
  return __ENV.TIER ? { tier: __ENV.TIER } : {};
}

export function readCode(res) {
  try {
    return res.json('code');
  } catch (e) {
    return null;
  }
}

export function recordHttpOutcome(res) {
  m.booking5xx.add(res.status === 0 || res.status >= 500, tierTags());
}

export function waitUntil(epochMs) {
  const wait = epochMs - Date.now();
  if (wait > 0) sleep(wait / 1000);
  m.bookingStartLag.add(Math.max(0, Date.now() - epochMs), tierTags());
}

export function classifyCreateFailure(res) {
  const code = readCode(res);
  const tags = tierTags();
  if (code === 4002) {
    m.bookingScheduleFull.add(1, tags);
    return 'full';
  }
  if (code === 4204) m.booking4204.add(1, tags);
  else if (code === 4202) m.booking4202.add(1, tags);
  else if (code === 4102) m.booking4102.add(1, tags);
  else if (code === 4103) m.booking4103.add(1, tags);
  else if (code === 4104) m.booking4104.add(1, tags);
  m.bookingUnexpected.add(1, tags);
  return 'unexpected';
}

export function issueBookingToken(authToken) {
  const res = issueToken(authToken);
  m.bookingTokenMs.add(res.timings.duration, tierTags());
  recordHttpOutcome(res);
  if (res.status !== 200 || readCode(res) !== 0) {
    return { ok: false, res };
  }
  return { ok: true, token: res.json('data.token'), res };
}

// 已有令牌后执行创建；202/5001/500 按文档做有限重试。
export function createWithToken(authToken, bookingToken, body) {
  let retries202 = 0;
  let retried5001 = false;
  let retried500 = false;

  while (true) {
    const res = createAppointment(authToken, bookingToken, body);
    m.bookingCreateMs.add(res.timings.duration, tierTags());
    recordHttpOutcome(res);

    if (res.status === 201 || res.status === 200) {
      return { ok: true, appointmentId: res.json('data.appointmentOrderId'), res };
    }
    if (res.status === 409) {
      return { ok: false, reason: classifyCreateFailure(res), res };
    }
    if (res.status === 202) {
      if (retried500 || retried5001) {
        m.booking202Stuck.add(1, tierTags());
        m.bookingUnexpected.add(1, tierTags());
        return { ok: false, reason: '202_stuck', res };
      }
      retries202 += 1;
      m.bookingRetry202.add(1, tierTags());
      if (retries202 > 5) {
        m.booking202Stuck.add(1, tierTags());
        m.bookingUnexpected.add(1, tierTags());
        return { ok: false, reason: '202_stuck', res };
      }
      sleep(0.1 * retries202);
      continue;
    }
    if (res.status === 503) {
      if (retried5001) {
        m.bookingUnexpected.add(1, tierTags());
        return { ok: false, reason: '5001', res };
      }
      retried5001 = true;
      m.bookingRetry5001.add(1, tierTags());
      sleep(0.1);
      continue;
    }
    if (res.status === 500) {
      if (retried500) {
        m.bookingUnexpected.add(1, tierTags());
        return { ok: false, reason: '500', res };
      }
      retried500 = true;
      sleep(0.1);
      continue;
    }
    m.bookingUnexpected.add(1, tierTags());
    return { ok: false, reason: `http_${res.status}`, res };
  }
}

export function createWithRetries(authToken, body) {
  const issued = issueBookingToken(authToken);
  if (!issued.ok) {
    m.bookingUnexpected.add(1, tierTags());
    return { ok: false, reason: 'token' };
  }
  return createWithToken(authToken, issued.token, body);
}

export function payWithRetries(authToken, appointmentId) {
  let retried5001 = 0;
  let retried500 = 0;
  while (true) {
    const res = payAppointment(authToken, appointmentId);
    m.bookingPayMs.add(res.timings.duration, tierTags());
    recordHttpOutcome(res);
    if (res.status === 200 && readCode(res) === 0) {
      return { ok: true, data: res.json('data'), res };
    }
    if (res.status === 409) {
      const code = readCode(res);
      if (code === 4003) m.bookingPay4003.add(1, tierTags());
      else if (code === 4004) m.bookingPay4004.add(1, tierTags());
      else m.bookingUnexpected.add(1, tierTags());
      return { ok: false, reason: `pay_${code}`, res };
    }
    if (res.status === 503 && retried5001 < 3) {
      retried5001 += 1;
      m.bookingRetry5001.add(1, tierTags());
      sleep(0.1 * retried5001);
      continue;
    }
    if (res.status === 500 && retried500 < 1) {
      retried500 += 1;
      sleep(0.1);
      continue;
    }
    m.bookingUnexpected.add(1, tierTags());
    return { ok: false, reason: `pay_http_${res.status}`, res };
  }
}

export function getDetailWithRetries(authToken, appointmentId) {
  let retried = 0;
  while (true) {
    const res = getAppointment(authToken, appointmentId);
    m.bookingDetailMs.add(res.timings.duration, tierTags());
    recordHttpOutcome(res);
    if (res.status === 200 && readCode(res) === 0) {
      return { ok: true, data: res.json('data'), res };
    }
    if ((res.status === 0 || res.status >= 500) && retried < 2) {
      retried += 1;
      m.bookingRetryNetwork.add(1, tierTags());
      sleep(0.1 * retried);
      continue;
    }
    m.bookingUnexpected.add(1, tierTags());
    return { ok: false, reason: `detail_http_${res.status}`, res };
  }
}

// 场景 A：一次完整业务迭代。只有创建、支付、详情都成功才算成功。
export function bookingFlow(authToken, patientId, slot) {
  const tags = tierTags();
  m.bookingAttempts.add(1, tags);
  const start = Date.now();

  const created = createWithRetries(authToken, {
    doctorId: slot.doctorId,
    appointmentDate: slot.appointmentDate,
    sessionType: slot.sessionType,
    patientId,
  });
  if (!created.ok) {
    m.bookingOk.add(false, tags);
    m.bookingIterationMs.add(Date.now() - start, tags);
    return;
  }
  m.bookingCreateOk.add(1, tags);

  const paid = payWithRetries(authToken, created.appointmentId);
  if (!paid.ok) {
    m.bookingOk.add(false, tags);
    m.bookingIterationMs.add(Date.now() - start, tags);
    return;
  }
  m.bookingPayOk.add(1, tags);

  const detail = getDetailWithRetries(authToken, created.appointmentId);
  if (!detail.ok) {
    m.bookingOk.add(false, tags);
    m.bookingIterationMs.add(Date.now() - start, tags);
    return;
  }
  m.bookingDetailOk.add(1, tags);
  m.bookingSuccess.add(1, tags);
  m.bookingOk.add(true, tags);
  m.bookingIterationMs.add(Date.now() - start, tags);
}

// 场景 A：按 scenario offset + 迭代序号分配唯一的 (patient, schedule)。
// 先用与 P*S 互质的乘数打散索引，避免同一患者或同一场次在短时间内形成人为热点。
const PAIR_MULTIPLIER = 104729;

export function pairFor(data, offset) {
  const index = offset + exec.scenario.iterationInTest;
  const patients = data.patients;
  const slots = data.slots;
  const limit = patients.length * slots.length;
  if (index >= limit) return null;
  const permuted = (index * PAIR_MULTIPLIER) % limit;
  return {
    index,
    patientId: patients[permuted % patients.length],
    slot: slots[Math.floor(permuted / patients.length) % slots.length],
  };
}
