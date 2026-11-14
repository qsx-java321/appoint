import { test, expect } from "@playwright/test";
import { backend } from "./backend";
import { appointment, hospital } from "../fixtures";
import type { Appointment, Visit } from "../../src/api/types";

test("15种预约详情状态保持预约、资金与就诊含义", async ({ page }) => {
  const state = await backend(page);
  const visit: Visit = {
    status: "NOT_CHECKED_IN",
    checkedInAt: null,
    startedAt: null,
    completedAt: null,
    canCheckIn: false,
    checkInOpensAt: "2026-11-16T13:30:00",
    checkInClosesAt: "2026-11-16T16:00:00",
  };
  const confirmed = {
    status: "CONFIRMED",
    paymentStatus: "PAID",
    workflowStatus: "CONFIRMED",
    canPay: false,
  };
  const cases: [string, Partial<Appointment>][] = [
    ["待支付", {}],
    [
      "支付处理中",
      {
        paymentStatus: "PAYING",
        workflowStatus: "PAYMENT_PROCESSING",
        canPay: false,
      },
    ],
    [
      "已支付，正在确认预约",
      {
        paymentStatus: "PAID",
        workflowStatus: "CONFIRMING_APPOINTMENT",
        canPay: false,
        canCancel: false,
      },
    ],
    ["预约已确认 · 待签到", { ...confirmed, visit }],
    [
      "预约已确认 · 待签到",
      {
        ...confirmed,
        serverTime: "2026-11-16T13:50:00",
        visit: { ...visit, canCheckIn: true },
      },
    ],
    [
      "预约已确认 · 候诊中",
      {
        ...confirmed,
        visit: {
          ...visit,
          status: "WAITING",
          checkedInAt: "2026-11-16T13:40:00",
        },
      },
    ],
    [
      "预约已确认 · 就诊中",
      { ...confirmed, visit: { ...visit, status: "IN_PROGRESS" } },
    ],
    [
      "预约已确认 · 已就诊",
      { ...confirmed, visit: { ...visit, status: "COMPLETED" } },
    ],
    [
      "预约已确认 · 待签到",
      {
        ...confirmed,
        serverTime: "2026-11-16T16:00:00",
        visit: { ...visit, canCheckIn: true },
      },
    ],
    [
      "预约已过期 · 退款处理中",
      {
        status: "EXPIRED",
        paymentStatus: "REFUND_PENDING",
        workflowStatus: "CANCELLED_REFUNDING",
        canPay: false,
        canCancel: false,
      },
    ],
    [
      "预约已过期 · 退款已完成",
      {
        status: "EXPIRED",
        paymentStatus: "REFUNDED",
        workflowStatus: "CANCELLED_REFUNDED",
        canPay: false,
        canCancel: false,
      },
    ],
    [
      "预约已取消 · 退款处理中",
      {
        status: "CANCELLED",
        paymentStatus: "REFUND_PENDING",
        workflowStatus: "CANCELLED_REFUNDING",
        canPay: false,
        canCancel: false,
      },
    ],
    [
      "预约已取消 · 退款已完成",
      {
        status: "CANCELLED",
        paymentStatus: "REFUNDED",
        workflowStatus: "CANCELLED_REFUNDED",
        canPay: false,
        canCancel: false,
      },
    ],
    [
      "预约已过期",
      {
        status: "EXPIRED",
        paymentStatus: "CLOSED",
        workflowStatus: "EXPIRED",
        canPay: false,
        canCancel: false,
      },
    ],
    [
      "预约已取消",
      {
        status: "CANCELLED",
        paymentStatus: "CLOSED",
        workflowStatus: null,
        canPay: false,
        canCancel: false,
      },
    ],
  ];
  for (const [expected, override] of cases) {
    state.appointment = appointment(override);
    state.confirmationReads = 0;
    await page.goto("/appointments/301");
    await expect(
      page.getByRole("heading", { name: expected, exact: true }),
    ).toBeVisible();
    if (override.serverTime === "2026-11-16T16:00:00")
      await expect(
        page.getByRole("button", { name: "到院签到" }),
      ).toBeDisabled();
  }
});

test("真实内容的核对页、就诊人表单与医生详情在四宽度重排", async ({
  page,
}, info) => {
  await backend(page, ["USER", "DOCTOR"]);
  for (const width of [320, 390, 736, 1024]) {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto(
      `/booking/doctors/101?hospitalId=${hospital.id}&departmentId=11`,
    );
    await expect(page.locator(".slot")).toHaveCount(12);
    if (width <= 560)
      await page
        .locator(".mobile-dates")
        .getByRole("button", { name: "2026-11-16" })
        .click();
    await page.locator(".day").nth(2).getByRole("button").nth(2).click();
    await page.getByRole("button", { name: "下一步：核对预约" }).click();
    await expect(page.locator(".patient-choice")).toHaveCount(1);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth + 1,
      ),
    ).toBe(true);
    if (width === 390)
      await info.attach("confirmation-mobile", {
        body: await page.screenshot({ fullPage: true }),
        contentType: "image/png",
      });
    await page.goto("/patients");
    await page.getByRole("button", { name: "新增就诊人" }).click();
    await expect(page.getByLabel("身份证号码", { exact: true })).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth + 1,
      ),
    ).toBe(true);
    await page.goto("/doctor");
    await page
      .locator("tbody tr")
      .nth(1)
      .getByRole("button", { name: "查看", exact: true })
      .click();
    await expect(page.locator(".doctor-detail")).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth + 1,
      ),
    ).toBe(true);
    await page.getByRole("button", { name: "关闭详情" }).click();
    await expect(page.locator("tbody tr")).toHaveCount(4);
  }
});

test("登录、注册与角色守卫", async ({ page }) => {
  await backend(page, ["USER"], false);
  await page.goto("/doctor");
  await expect(page).toHaveURL(/\/login\?redirect=/);
  await page.getByRole("link", { name: "注册账号", exact: true }).click();
  await page.getByLabel("邮箱", { exact: true }).fill("new@example.com");
  await page.getByLabel("密码", { exact: true }).fill("example123");
  await page.getByRole("button", { name: "注册账号", exact: true }).click();
  await expect(page.getByRole("heading", { name: "欢迎登录" })).toBeVisible();
  await page.getByLabel("密码", { exact: true }).fill("example123");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "无权访问此页面" }),
  ).toBeVisible();
  await page.goto("/admin/catalog");
  await expect(
    page.getByRole("heading", { name: "无权访问此页面" }),
  ).toBeVisible();
});

test("完整预约、新增就诊人返回、未知结果原键重试、模拟支付确认", async ({
  page,
}) => {
  const state = await backend(page);
  state.unknownCreate = true;
  await page.goto("/booking");
  await page.getByRole("link", { name: "选择医院" }).click();
  await page.getByRole("link", { name: "查看场次" }).click();
  await expect(page.locator(".slot")).toHaveCount(12);
  await page.locator(".day").nth(2).getByRole("button").nth(2).click();
  await page.getByRole("button", { name: "下一步：核对预约" }).click();
  await expect(
    page.getByRole("button", { name: "提交预约，前往支付" }),
  ).toBeDisabled();
  await page.getByRole("link", { name: "新增就诊人" }).click();
  await page.getByLabel("姓名", { exact: true }).fill("新增就诊人");
  await page
    .getByLabel("身份证号码", { exact: true })
    .fill("11010519491231002X");
  await page.getByLabel("联系电话").fill("13800138000");
  await page.getByLabel("联系邮箱").fill("new@example.com");
  await page.getByRole("button", { name: "保存就诊人" }).click();
  await expect(
    page.getByRole("heading", { name: "核对这次预约" }),
  ).toBeVisible();
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "提交预约，前往支付" }).click();
  await expect(
    page.getByRole("button", { name: "核实原提交结果" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "核实原提交结果" }).click();
  await expect(page).toHaveURL(/\/appointments\/301\/payment/);
  expect(state.keys[0]).toBe(state.keys[1]);
  expect(state.creates).toBe(2);
  await page.getByRole("button", { name: "发起模拟支付" }).click();
  await expect(
    page.getByRole("heading", { name: "支付处理中", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "模拟渠道成功" }).click();
  await expect(
    page.getByRole("heading", { name: "已支付，正在确认预约" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "取消预约" })).toHaveCount(0);
  await page.getByRole("button", { name: "刷新进度" }).click();
  await expect(
    page.getByRole("heading", { name: "预约已确认 · 待签到" }),
  ).toBeVisible();
  await expect(page.locator(".ticket-date")).toHaveText("2026-11-16");
  await expect(page.locator("main")).toContainText("新增就诊人");
});

test("编辑就诊人省略未修改证件并正确启停", async ({ page }) => {
  const state = await backend(page);
  page.on("dialog", (dialog) => dialog.accept());
  await page.goto("/patients");
  await page.getByRole("button", { name: "编辑资料" }).click();
  await expect(
    page.getByRole("textbox", { name: "身份证号码", exact: true }),
  ).toHaveCount(0);
  await page.getByLabel("联系电话").fill("13900139000");
  await page.getByRole("button", { name: "保存就诊人" }).click();
  await expect(
    page.getByRole("button", { name: "停用", exact: true }),
  ).toBeVisible();
  expect(state.patientWrites[0]).not.toHaveProperty("documentNo");
  expect(state.patientWrites[0]).not.toHaveProperty("documentNoMasked");
  await page.getByRole("button", { name: "停用", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "启用", exact: true }),
  ).toBeVisible();
});

test("到点禁用、24小时严格边界、退款受理不提前完成", async ({ page }) => {
  const state = await backend(page);
  page.on("dialog", (dialog) => dialog.accept());
  state.appointment = appointment({ serverTime: "2026-11-14T09:55:00" });
  await page.goto("/appointments/301");
  await expect(page.getByRole("link", { name: "去支付" })).toHaveCount(0);
  state.appointment = appointment({
    status: "CONFIRMED",
    paymentStatus: "PAID",
    workflowStatus: "CONFIRMED",
    canPay: false,
    canCancel: true,
    appointmentDate: "2026-11-15",
    startTime: "09:40:00",
  });
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "预约已确认 · 待签到" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "取消预约" })).toHaveCount(0);
  state.appointment = appointment({
    status: "CONFIRMED",
    paymentStatus: "PAID",
    workflowStatus: "CONFIRMED",
    canPay: false,
    canCancel: true,
  });
  await page.reload();
  await page.getByRole("button", { name: "取消预约" }).click();
  await expect(
    page.getByRole("heading", { name: "预约已取消 · 退款处理中" }),
  ).toBeVisible();
  await expect(page.getByRole("heading", { name: /退款已完成/ })).toHaveCount(
    0,
  );
});

test("医生按钮矩阵、并行接诊、号码顺序和只读详情", async ({ page }) => {
  await backend(page, ["USER", "DOCTOR"]);
  await page.goto("/doctor");
  const rows = page.locator("tbody tr");
  await expect(rows).toHaveCount(4);
  await expect(
    rows.nth(0).getByRole("button", { name: "开始接诊" }),
  ).toBeDisabled();
  await expect(
    rows.nth(1).getByRole("button", { name: "开始接诊" }),
  ).toBeEnabled();
  await expect(
    rows.nth(2).getByRole("button", { name: "完成接诊" }),
  ).toBeEnabled();
  await rows.nth(1).getByRole("button", { name: "开始接诊" }).click();
  await expect(
    rows.nth(1).getByRole("button", { name: "完成接诊" }),
  ).toBeEnabled();
  await rows.nth(1).getByRole("button", { name: "完成接诊" }).click();
  await expect(rows.nth(1)).toContainText("已就诊");
  await expect(page.locator('td[data-label="序号"]')).toHaveText([
    "001",
    "002",
    "003",
    "004",
  ]);
  await rows.nth(1).getByRole("button", { name: "查看", exact: true }).click();
  await expect(page.locator(".doctor-detail")).toBeVisible();
  await expect(page.locator(".doctor-detail button")).toHaveCount(1);
  await page.getByRole("button", { name: "关闭详情" }).click();
  await expect(rows).toHaveCount(4);
});

test("管理员目录引用错误、设置与首次绑定", async ({ page }) => {
  const state = await backend(page, ["USER", "ADMIN"]);
  page.on("dialog", (dialog) => dialog.accept());
  await page.goto("/admin/catalog");
  await page.getByRole("button", { name: "删除", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("仍被引用");
  await page.getByRole("link", { name: "统一预约设置", exact: true }).click();
  await page.getByLabel("每场容量").fill("30");
  await page.getByLabel("挂号费（元）").fill("12.00");
  await page.getByRole("button", { name: "核对并保存设置" }).click();
  await expect(page.getByRole("status")).toContainText("已保存");
  expect(state.settings.capacityPerSession).toBe(30);
  await page.getByRole("link", { name: "医生账号绑定" }).click();
  await page.getByLabel("医生档案").selectOption("101");
  await page.getByLabel("已存在的账号ID").fill("12");
  await page.getByRole("button", { name: "核对并绑定账号" }).click();
  await expect(
    page.getByRole("heading", { name: "本次绑定成功" }),
  ).toBeVisible();
  expect(state.bindings).toEqual([{ doctorId: "101", userId: "12" }]);
});

test("网络错误保留重试入口，401退出会话", async ({ page }) => {
  const state = await backend(page);
  state.failHospitals = true;
  await page.goto("/booking");
  await expect(page.getByRole("alert")).toContainText("目录暂时不可用");
  state.failHospitals = false;
  await page.getByRole("button", { name: "重试", exact: true }).click();
  await expect(page.getByRole("link", { name: "选择医院" })).toBeVisible();
  state.unauthorized = true;
  await page.goto("/appointments");
  await expect(page).toHaveURL(/\/login/);
});

test("14类页面在四种宽度均可用且没有脚本错误", async ({ page }) => {
  test.setTimeout(120000);
  const state = await backend(page, ["USER", "ADMIN", "DOCTOR"]),
    errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const paths = [
    "/login",
    "/register",
    "/booking",
    `/booking/hospitals/${hospital.id}`,
    `/booking/doctors/101?hospitalId=${hospital.id}&departmentId=11`,
    "/booking/confirm",
    "/appointments/301/payment",
    "/appointments",
    "/appointments/301",
    "/patients",
    "/account",
    "/doctor",
    "/admin/catalog",
    "/admin/booking-settings",
    "/admin/doctor-accounts",
  ];
  for (const width of [320, 390, 736, 1024])
    for (const path of paths) {
      await page.setViewportSize({ width, height: 1000 });
      await page.goto(path);
      await expect(page.locator("h1")).toBeVisible();
      await expect(
        page.getByText("正在加载，请稍候…", { exact: true }),
      ).toHaveCount(0);
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth > window.innerWidth + 1,
      );
      expect(overflow, `${path} ${width}px`).toBe(false);
    }
  expect(errors).toEqual([]);
  state.appointment = appointment();
});
