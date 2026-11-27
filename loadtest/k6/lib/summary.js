function value(metric, key, fallback = '-') {
  const values = metric && metric.values ? metric.values : {};
  return values[key] === undefined ? fallback : values[key];
}

function ms(n) {
  return typeof n === 'number' ? `${n.toFixed(2)}ms` : '-';
}

export function readBaselineSummary(data) {
  const metrics = data.metrics || {};
  const http = metrics.http_reqs || {};
  const failed = metrics.http_req_failed || {};
  const checks = metrics.checks || {};
  const duration = metrics.http_req_duration || {};

  const lines = [
    '=== 场景 C：读接口基线 ===',
    `HTTP 请求数: ${value(http, 'count', 0)}，平均 ${value(http, 'rate', 0).toFixed(1)} req/s`,
    `HTTP 失败率: ${(value(failed, 'rate', 0) * 100).toFixed(3)}%`,
    `业务校验通过率: ${(value(checks, 'rate', 0) * 100).toFixed(3)}%`,
    `业务校验失败数: ${value(metrics.read_business_error, 'count', 0)}`,
    `HTTP 耗时 p95: ${ms(value(duration, 'p(95)'))}，p99: ${ms(value(duration, 'p(99)'))}`,
    '',
    '各接口耗时:',
  ];

  const endpointMetrics = [
    ['schedules', 'read_schedules_ms'],
    ['hospitals', 'read_hospitals_ms'],
    ['departments', 'read_departments_ms'],
    ['doctors', 'read_doctors_ms'],
    ['appointment', 'read_appointment_ms'],
    ['my_appointments', 'read_my_appointments_ms'],
    ['notifications', 'read_notifications_ms'],
  ];
  for (const [name, metricName] of endpointMetrics) {
    const metric = metrics[metricName];
    if (!metric) continue;
    lines.push(`  ${name}: p95=${ms(value(metric, 'p(95)'))} p99=${ms(value(metric, 'p(99)'))} avg=${ms(value(metric, 'avg'))}`);
  }

  return {
    'loadtest/results/read-baseline-summary.json': JSON.stringify(data, null, 2),
    stdout: `${lines.join('\n')}\n`,
  };
}
