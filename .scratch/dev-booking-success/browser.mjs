import { chromium, expect as baseExpect } from '../../frontend/node_modules/@playwright/test/index.mjs';
import { mkdirSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const expect = baseExpect.configure({ timeout: 15000 });
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, '.scratch/dev-booking-success/evidence');
mkdirSync(evidence, { recursive: true });
const run = 'dev-booking-' + new Date().toISOString().replace(/\D/g, '').slice(0, 14) + '-' + randomBytes(3).toString('hex');
const actor = { email: run + '@example.local', password: 'Dev-' + randomBytes(16).toString('hex') };
const privatePath = path.join(root, '.local-env/dev-booking-actor.json');
writeFileSync(privatePath, JSON.stringify({ run, actor }, null, 2) + '\n', { mode: 0o600 });
const prefix = '11010519900101852';
const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
const documentNo = prefix + '10X98765432'[[...prefix].reduce((sum, n, i) => sum + Number(n) * weights[i], 0) % 11];
const http = [], errors = [], tasks = [], checks = [];
const result = { run, passed: false };
const save = (name, value) => writeFileSync(path.join(evidence, name), JSON.stringify(value, null, 2) + '\n');
const check = (name) => { checks.push({ name, passed: true }); console.log('PASS ' + name); };
const browser = await chromium.launch({
  executablePath: '/home/qsx/.cache/puppeteer/chrome/linux-156.0.8078.47/chrome-linux64/chrome',
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
});
const context = await browser.newContext({ baseURL: 'http://127.0.0.1:5173', viewport: { width: 1280, height: 900 } });
const page = await context.newPage();
page.on('pageerror', (error) => errors.push(error.message));
page.on('response', (response) => {
  const url = new URL(response.url());
  if (!url.pathname.startsWith('/api/')) return;
  tasks.push((async () => {
    const body = await response.json();
    http.push({ method: response.request().method(), path: url.pathname, status: response.status(),
      code: body.code, workflow: body.data?.workflowStatus, payment: body.data?.paymentStatus, visit: body.data?.visit?.status });
  })().catch((error) => errors.push(error.message)));
});
async function readApi(route) {
  return page.evaluate(async (route) => {
    const session = JSON.parse(sessionStorage.getItem('appoint.session.v1'));
    const response = await fetch('/api/v1' + route, { headers: { Authorization: 'Bearer ' + session.token } });
    const body = await response.json();
    if (!response.ok || body.code !== 0) throw new Error('查询失败 ' + route + ': ' + response.status);
    return body.data;
  }, route);
}
try {
  await page.goto('/register');
  await page.getByLabel('邮箱', { exact: true }).fill(actor.email);
  await page.getByLabel('密码', { exact: true }).fill(actor.password);
  const registered = page.waitForResponse((r) => r.url().endsWith('/auth/register') && r.request().method() === 'POST');
  await page.getByRole('button', { name: '注册账号', exact: true }).click();
  const registeredResponse = await registered;
  expect(registeredResponse.status()).toBe(201);
  result.userId = (await registeredResponse.json()).data.id;
  await expect(page.getByRole('heading', { name: '欢迎登录' })).toBeVisible();
  await page.goto('/login');
  await page.getByLabel('邮箱', { exact: true }).fill(actor.email);
  await page.getByLabel('密码', { exact: true }).fill(actor.password);
  const loggedIn = page.waitForResponse((r) => r.url().endsWith('/auth/login') && r.request().method() === 'POST');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  const loginResponse = await loggedIn;
  expect(loginResponse.status()).toBe(200);
  await expect(page).toHaveURL((url) => url.pathname === '/booking');
  expect(await page.evaluate(() => !!JSON.parse(sessionStorage.getItem('appoint.session.v1'))?.token)).toBe(true);
  check('真实页面注册与登录成功');

  await page.goto('/patients');
  await page.getByRole('button', { name: '新增就诊人' }).click();
  await page.getByLabel('姓名', { exact: true }).fill('dev预约验收就诊人');
  await page.getByLabel('身份证号码', { exact: true }).fill(documentNo);
  await page.getByLabel('联系电话').fill('13800138000');
  await page.getByLabel('联系邮箱').fill('synthetic@example.local');
  const patientCreated = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/patients' && r.request().method() === 'POST');
  await page.getByRole('button', { name: '保存就诊人' }).click();
  const patientResponse = await patientCreated;
  expect(patientResponse.status()).toBe(201);
  result.patientId = (await patientResponse.json()).data.id;
  await expect(page.getByRole('button', { name: '编辑资料' })).toBeVisible();
  check('真实页面新增就诊人成功');

  await page.goto('/booking');
  await page.getByRole('link', { name: '选择医院' }).first().click();
  await expect(page.getByRole('heading', { name: '选择科室' })).toBeVisible();
  await page.locator('.department-nav button').first().click();
  await page.getByRole('link', { name: '查看场次' }).first().click();
  await expect(page.locator('.slot')).toHaveCount(12);
  const doctorId = new URL(page.url()).pathname.split('/').at(-1);
  const schedules = await readApi('/doctors/' + doctorId + '/schedules');
  // 优先明天，避开当天截止；只使用页面显示且可预约的场次。
  const today = schedules[0].serverTime.slice(0, 10);
  const slot = schedules.find((s) => s.appointmentDate > today && s.bookable);
  expect(slot).toBeTruthy();
  result.selected = { doctorId, ...slot };
  const dates = [...new Set(schedules.map((s) => s.appointmentDate))];
  const types = ['MORNING_FIRST', 'MORNING_SECOND', 'AFTERNOON_FIRST', 'AFTERNOON_SECOND'];
  await page.locator('.day').nth(dates.indexOf(slot.appointmentDate)).getByRole('button').nth(types.indexOf(slot.sessionType)).click();
  await page.getByRole('button', { name: '下一步：核对预约' }).click();
  await expect(page.locator('.patient-choice')).toHaveCount(1);
  await page.getByRole('checkbox').check();
  check('医院、科室、医生、三天十二场次及就诊人核对成功');

  const created = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/appointments' && r.request().method() === 'POST');
  await page.getByRole('button', { name: '提交预约，前往支付' }).click();
  const createdResponse = await created;
  expect(createdResponse.status()).toBe(201);
  const order = (await createdResponse.json()).data;
  result.appointmentId = order.appointmentOrderId;
  result.paymentId = order.paymentOrderId;
  await expect(page).toHaveURL(new RegExp('/appointments/' + result.appointmentId + '/payment$'));
  await expect(page.getByRole('heading', { name: '待支付', exact: true })).toBeVisible();
  expect(order.appointmentStatus).toBe('PENDING_PAYMENT');
  expect(order.amount).toBe(slot.registrationFee);
  check('预约单和支付单创建成功，费用与场次一致');

  await page.getByRole('button', { name: '发起模拟支付' }).click();
  await expect(page.getByRole('button', { name: '模拟渠道成功' })).toBeVisible();
  await page.getByRole('button', { name: '模拟渠道成功' }).click();
  await expect(page.getByRole('heading', { name: '预约已确认 · 待签到', exact: true })).toBeVisible({ timeout: 45000 });
  const detail = await readApi('/appointments/' + result.appointmentId);
  expect(detail.status).toBe('CONFIRMED');
  expect(detail.paymentStatus).toBe('PAID');
  expect(detail.workflowStatus).toBe('CONFIRMED');
  expect(detail.visitNumber).toBeGreaterThan(0);
  expect(detail.visit.status).toBe('NOT_CHECKED_IN');
  expect(detail.paymentAttemptCount).toBe(1);
  result.confirmed = { status: detail.status, paymentStatus: detail.paymentStatus, workflowStatus: detail.workflowStatus,
    visitNumber: detail.visitNumber, visit: detail.visit, appointmentDate: detail.appointmentDate,
    startTime: detail.startTime, endTime: detail.endTime, amount: detail.amount, currency: detail.currency,
    doctor: detail.doctor, paymentAttemptCount: detail.paymentAttemptCount, serverTime: detail.serverTime };
  await page.screenshot({ path: path.join(evidence, 'booking-success.png'), fullPage: true });
  check('MockPay付款、正式异步确认、分配就诊序号成功，停止在待签到');

  await page.goto('/appointments');
  await expect(page.locator('article')).toHaveCount(1);
  await expect(page.locator('article')).toContainText('预约已确认');
  const mine = await readApi('/appointments/my');
  expect(mine.map((a) => a.id)).toEqual([result.appointmentId]);
  await page.goto('/appointments/' + result.appointmentId);
  await expect(page.getByRole('heading', { name: '预约已确认 · 待签到', exact: true })).toBeVisible();
  check('我的预约及重新打开详情均保持预约成功');
  expect(errors).toEqual([]);
  result.passed = true;
} catch (error) {
  result.error = error.message;
  await page.screenshot({ path: path.join(evidence, 'failure.png'), fullPage: true }).catch(() => {});
  process.exitCode = 1;
  console.error(error.message);
} finally {
  await Promise.allSettled(tasks);
  save('http-summary.json', http);
  save('page-errors.json', errors);
  save('checks.json', checks);
  save('result.json', result);
  writeFileSync(privatePath, JSON.stringify({ run, actor, userId: result.userId, appointmentId: result.appointmentId }, null, 2) + '\n', { mode: 0o600 });
  await browser.close();
}
