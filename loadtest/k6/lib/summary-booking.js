function metric(data, name) {
  return data.metrics && data.metrics[name] ? data.metrics[name].values || {} : {};
}

function num(values, key, fallback = 0) {
  return values[key] === undefined ? fallback : values[key];
}

function ms(n) {
  return typeof n === 'number' ? `${n.toFixed(2)}ms` : '-';
}

export function bookingSummary(data, scenario) {
  const lines = [];
  const file = scenario === 'b'
    ? 'loadtest/results/scenario-b-summary.json'
    : 'loadtest/results/scenario-a-summary.json';

  if (scenario === 'b') {
    const attempts = num(metric(data, 'booking_attempts'), 'count');
    const success = num(metric(data, 'booking_success'), 'count');
    const full = num(metric(data, 'booking_schedule_full'), 'count');
    const unexpected = num(metric(data, 'booking_unexpected'), 'count');
    const retry202 = num(metric(data, 'booking_retry_202'), 'count');
    const retry5001 = num(metric(data, 'booking_retry_5001'), 'count');
    const stuck = num(metric(data, 'booking_202_stuck'), 'count');
    const outcome = num(metric(data, 'booking_outcome_ok'), 'rate');
    const fivexx = num(metric(data, 'booking_5xx'), 'rate');
    const lag = metric(data, 'booking_start_lag_ms');

    lines.push('=== 场景 B：单场次抢号 ===');
    lines.push(`尝试: ${attempts}`);
    lines.push(`成功: ${success}`);
    lines.push(`4002 约满: ${full}`);
    lines.push(`unexpected: ${unexpected}`);
    lines.push(`202 重试: ${retry202}，5001 重试: ${retry5001}，202 卡死: ${stuck}`);
    lines.push(`预期结果率: ${(outcome * 100).toFixed(3)}%，5xx 率: ${(fivexx * 100).toFixed(3)}%`);
    lines.push(`并发启动 lag p95: ${ms(lag['p(95)'])}，p99: ${ms(lag['p(99)'])}`);
  } else {
    lines.push('=== 场景 A：下单主链路容量 ===');
    const tiers = Object.keys(data.metrics || {})
      .filter((key) => key.startsWith('booking_ok{') || key.startsWith('http_req_duration{tier:'))
      .sort();
    for (const key of tiers) {
      if (key.startsWith('booking_ok{')) {
        const values = metric(data, key);
        lines.push(`${key}: rate=${(num(values, 'rate') * 100).toFixed(3)}%`);
      } else {
        const values = metric(data, key);
        lines.push(`${key}: p95=${ms(values['p(95)'])} p99=${ms(values['p(99)'])}`);
      }
    }
    const attempts = num(metric(data, 'booking_attempts'), 'count');
    const success = num(metric(data, 'booking_success'), 'count');
    const fivexx = num(metric(data, 'booking_5xx'), 'rate');
    lines.push(`总尝试: ${attempts}，成功: ${success}，5xx 率: ${(fivexx * 100).toFixed(3)}%`);
  }

  return {
    [file]: JSON.stringify(data, null, 2),
    stdout: `${lines.join('\n')}\n`,
  };
}
