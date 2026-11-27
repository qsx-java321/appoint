import { Counter, Rate, Trend } from 'k6/metrics';

// 场景 B 和 A 共用；k6 每次只运行一个脚本，指标名不会互相冲突。
export const bookingAttempts = new Counter('booking_attempts');
export const bookingSuccess = new Counter('booking_success');
export const bookingCreateOk = new Counter('booking_create_ok');
export const bookingPayOk = new Counter('booking_pay_ok');
export const bookingDetailOk = new Counter('booking_detail_ok');
export const bookingScheduleFull = new Counter('booking_schedule_full');
export const booking4204 = new Counter('booking_4204');
export const booking4202 = new Counter('booking_4202');
export const booking4102 = new Counter('booking_4102');
export const booking4103 = new Counter('booking_4103');
export const booking4104 = new Counter('booking_4104');
export const booking202Stuck = new Counter('booking_202_stuck');
export const bookingRetry202 = new Counter('booking_retry_202');
export const bookingRetry5001 = new Counter('booking_retry_5001');
export const bookingRetryNetwork = new Counter('booking_retry_network');
export const bookingPay4003 = new Counter('booking_pay_4003');
export const bookingPay4004 = new Counter('booking_pay_4004');
export const bookingUnexpected = new Counter('booking_unexpected');

export const bookingOutcomeOk = new Rate('booking_outcome_ok');
export const bookingOk = new Rate('booking_ok');
export const booking5xx = new Rate('booking_5xx');

export const bookingStartLag = new Trend('booking_start_lag_ms');
export const bookingTokenMs = new Trend('booking_token_ms');
export const bookingCreateMs = new Trend('booking_create_ms');
export const bookingPayMs = new Trend('booking_pay_ms');
export const bookingDetailMs = new Trend('booking_detail_ms');
export const bookingIterationMs = new Trend('booking_iteration_ms');
