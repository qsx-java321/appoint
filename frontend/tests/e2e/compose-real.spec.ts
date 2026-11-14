import { test, expect, type Page, type Response } from "@playwright/test";
import { readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

interface Actor {
  email: string;
  password: string;
  id?: string;
}
const config = JSON.parse(
  readFileSync(process.env.COMPOSE_E2E_CONFIG!, "utf8"),
) as {
  actors: Record<"admin" | "doctor" | "user", Actor>;
  runId: string;
  date: string;
  documentNo: string;
  evidence: string;
};
const hospitalName = `联调医院-${config.runId}`;
const departmentName = "联调科室";
const doctorName = "联调医生";

async function login(page: Page, actor: Actor) {
  await page.goto("/login");
  await page.getByLabel("邮箱", { exact: true }).fill(actor.email);
  await page.getByLabel("密码", { exact: true }).fill(actor.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(page).toHaveURL(/\/booking$/);
}
async function readApi<T>(page: Page, route: string): Promise<T> {
  return page.evaluate(async (route) => {
    const session = JSON.parse(sessionStorage.getItem("appoint.session.v1")!);
    const response = await fetch("/api/v1" + route, {
      headers: { Authorization: "Bearer " + session.token },
    });
    const body = await response.json();
    if (!response.ok || body.code !== 0)
      throw new Error(`查询${route}失败：${response.status}`);
    return body.data;
  }, route);
}
async function saveCatalog(page: Page) {
  const response = page.waitForResponse(
    (r) =>
      r.url().includes("/api/v1/admin/") &&
      ["POST", "PATCH"].includes(r.request().method()),
  );
  await page.getByRole("button", { name: "保存资料", exact: true }).click();
  const result = await response;
  expect(result.ok()).toBe(true);
  await expect(page.getByRole("status")).toHaveText("目录资料已保存。");
  return (await result.json()).data as { id: string; doctorNo?: string };
}
async function createBooking(page: Page, day: number, type = 2) {
  await page.goto("/booking");
  await page
    .locator("article")
    .filter({
      has: page.getByRole("heading", { name: hospitalName, exact: true }),
    })
    .getByRole("link", { name: "选择医院" })
    .click();
  await page.getByRole("link", { name: "查看场次" }).click();
  await expect(page.locator(".slot")).toHaveCount(12);
  await page.locator(".day").nth(day).getByRole("button").nth(type).click();
  await page.getByRole("button", { name: "下一步：核对预约" }).click();
  await expect(page.locator(".patient-choice")).toHaveCount(1);
  await page.getByRole("checkbox").check();
  const created = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === "/api/v1/appointments" &&
      r.request().method() === "POST",
  );
  await page.getByRole("button", { name: "提交预约，前往支付" }).click();
  const result = await created;
  expect(result.status()).toBe(201);
  const id = (await result.json()).data.appointmentOrderId as string;
  await expect(page).toHaveURL(new RegExp(`/appointments/${id}/payment$`));
  await expect(
    page.getByRole("heading", { name: "待支付", exact: true }),
  ).toBeVisible();
  return id;
}
async function payAndConfirm(page: Page, firstFail = false) {
  await page.getByRole("button", { name: "发起模拟支付" }).click();
  await expect(
    page.getByRole("button", { name: "模拟渠道成功" }),
  ).toBeVisible();
  if (firstFail) {
    await page.getByRole("button", { name: "模拟渠道失败" }).click();
    await expect(
      page.getByRole("button", { name: "发起模拟支付" }),
    ).toBeVisible({ timeout: 45000 });
    await expect(
      page.getByText("本次尝试失败，可在仍有支付资格时重试。"),
    ).toBeVisible();
    await page.getByRole("button", { name: "发起模拟支付" }).click();
  }
  await page.getByRole("button", { name: "模拟渠道成功" }).click();
  await expect(
    page.getByRole("heading", { name: "预约已确认 · 待签到", exact: true }),
  ).toBeVisible({ timeout: 45000 });
}

test("真实Compose：管理目录与绑定、支付失败重试、退款、签到接诊、双端一致和权限", async ({
  page,
  browser,
}, info) => {
  test.setTimeout(240000);
  const records: {
    method: string;
    path: string;
    status: number;
    code?: number;
    workflow?: string;
    payment?: string;
    visit?: string;
  }[] = [];
  const tasks: Promise<void>[] = [];
  const errors: string[] = [];
  const monitor = (p: Page) => {
    p.on("pageerror", (e) => errors.push(e.message));
    p.on("dialog", (d) => d.accept());
    p.on("response", (response: Response) => {
      const url = new URL(response.url());
      if (!url.pathname.startsWith("/api/")) return;
      tasks.push(
        (async () => {
          let body: {
            code?: number;
            data?: {
              workflowStatus?: string;
              paymentStatus?: string;
              visit?: { status?: string };
            };
          } = {};
          try {
            body = await response.json();
          } catch {
            /* 只记录状态，不读取非JSON页面内容。 */
          }
          records.push({
            method: response.request().method(),
            path: url.pathname,
            status: response.status(),
            code: body.code,
            workflow: body.data?.workflowStatus,
            payment: body.data?.paymentStatus,
            visit: body.data?.visit?.status,
          });
        })(),
      );
    });
  };
  monitor(page);
  const created: Record<string, string> = {};
  try {
    await test.step("管理员维护目录、统一设置与首次绑定", async () => {
      await login(page, config.actors.admin);
      await page.goto("/admin/catalog");
      await page.getByRole("button", { name: "新增医院", exact: true }).click();
      await page.getByLabel("名称", { exact: true }).fill(hospitalName);
      await page.getByLabel("地址", { exact: true }).fill("联调测试地址100号");
      created.hospital = (await saveCatalog(page)).id;
      const hospitalRow = page
        .locator("tbody tr")
        .filter({ hasText: hospitalName });
      await hospitalRow
        .getByRole("button", { name: "编辑", exact: true })
        .click();
      await page.getByLabel("地址", { exact: true }).fill("联调测试地址101号");
      await saveCatalog(page);
      await expect(hospitalRow).toContainText("联调测试地址101号");
      await page.getByRole("button", { name: "科室", exact: true }).click();
      await page.getByLabel("所属医院").selectOption(created.hospital);
      await page.getByRole("button", { name: "新增科室" }).click();
      await page.getByLabel("名称", { exact: true }).fill(departmentName);
      created.department = (await saveCatalog(page)).id;
      await page.getByRole("button", { name: "医生", exact: true }).click();
      await page.getByLabel("所属科室").selectOption(created.department);
      await page.getByRole("button", { name: "新增医生" }).click();
      await page.getByLabel("名称", { exact: true }).fill(doctorName);
      await page.getByLabel("职称", { exact: true }).fill("主任医师");
      await page.getByLabel("简介").fill("仅用于隔离数据库的前端真实联调。");
      created.doctor = (await saveCatalog(page)).id;
      await page.getByRole("button", { name: "医院", exact: true }).click();
      const deletion = page.waitForResponse(
        (r) => r.request().method() === "DELETE",
      );
      await page
        .locator("tbody tr")
        .filter({ hasText: hospitalName })
        .getByRole("button", { name: "删除", exact: true })
        .click();
      expect((await deletion).status()).toBe(409);
      await expect(page.getByRole("alert")).toBeVisible();
      await page.goto("/admin/booking-settings");
      await page.getByLabel("每场容量").fill("4");
      await page.getByLabel("挂号费（元）").fill("12.34");
      await page.getByRole("button", { name: "核对并保存设置" }).click();
      await expect(page.getByRole("status")).toHaveText("统一预约设置已保存。");
      await page.goto("/admin/doctor-accounts");
      await page.getByLabel("所属医院").selectOption(created.hospital);
      await page.getByLabel("所属科室").selectOption(created.department);
      await page.getByLabel("医生档案").selectOption(created.doctor);
      await page.getByLabel("已存在的账号ID").fill(config.actors.doctor.id!);
      await page.getByRole("button", { name: "核对并绑定账号" }).click();
      await expect(
        page.getByRole("heading", { name: "本次绑定成功" }),
      ).toBeVisible();
    });
    const userContext = await browser.newContext(),
      user = await userContext.newPage();
    monitor(user);
    await test.step("普通用户注册、就诊人新增编辑与启停", async () => {
      await user.goto("/register");
      await user
        .getByLabel("邮箱", { exact: true })
        .fill(config.actors.user.email);
      await user
        .getByLabel("密码", { exact: true })
        .fill(config.actors.user.password);
      await user.getByRole("button", { name: "注册账号", exact: true }).click();
      await expect(
        user.getByRole("heading", { name: "欢迎登录" }),
      ).toBeVisible();
      await login(user, config.actors.user);
      await user.goto("/patients");
      await user.getByRole("button", { name: "新增就诊人" }).click();
      await user.getByLabel("姓名", { exact: true }).fill("联调就诊人");
      await user
        .getByLabel("身份证号码", { exact: true })
        .fill(config.documentNo);
      await user.getByLabel("联系电话").fill("13800138000");
      await user.getByLabel("联系邮箱").fill("patient@example.com");
      await user.getByRole("button", { name: "保存就诊人" }).click();
      await expect(
        user.getByRole("button", { name: "编辑资料" }),
      ).toBeVisible();
      await user.getByRole("button", { name: "编辑资料" }).click();
      await user.getByLabel("联系电话").fill("13900139000");
      await user.getByRole("button", { name: "保存就诊人" }).click();
      await user.getByRole("button", { name: "停用", exact: true }).click();
      await expect(
        user.getByRole("button", { name: "启用", exact: true }),
      ).toBeVisible();
      await user.getByRole("button", { name: "启用", exact: true }).click();
      await expect(
        user.getByRole("button", { name: "停用", exact: true }),
      ).toBeVisible();
    });
    await test.step("未来场次支付失败重试、真实异步确认与取消退款", async () => {
      created.refundOrder = await createBooking(user, 2);
      await expect(user.locator(".facts").last()).toContainText("12.34");
      await payAndConfirm(user, true);
      const details = await readApi<{
        paymentAttemptCount: number;
        visitNumber: number;
        canCancel: boolean;
      }>(user, `/appointments/${created.refundOrder}`);
      expect(details.paymentAttemptCount).toBe(2);
      expect(details.visitNumber).toBe(1);
      expect(details.canCancel).toBe(true);
      const cancel = user.waitForResponse(
        (r) => r.url().endsWith("/cancel") && r.request().method() === "POST",
      );
      await user.getByRole("button", { name: "取消预约" }).click();
      expect((await cancel).status()).toBe(202);
      await expect(
        user.getByRole("heading", {
          name: "预约已取消 · 退款已完成",
          exact: true,
        }),
      ).toBeVisible({ timeout: 45000 });
      const result = await readApi<{
        refund: { status: string; completedAt: string };
        paymentStatus: string;
      }>(user, `/appointments/${created.refundOrder}`);
      expect(result.paymentStatus).toBe("REFUNDED");
      expect(result.refund.completedAt).toBeTruthy();
      await user.screenshot({
        path: path.join(config.evidence, "refund-completed.png"),
        fullPage: true,
      });
    });
    await test.step("当天场次支付、签到与未支付取消", async () => {
      created.visitOrder = await createBooking(user, 0);
      await payAndConfirm(user);
      await expect(user.getByRole("button", { name: "取消预约" })).toHaveCount(
        0,
      );
      await user.getByRole("button", { name: "到院签到", exact: true }).click();
      await expect(
        user.getByRole("heading", { name: "预约已确认 · 候诊中", exact: true }),
      ).toBeVisible();
      const prior = await readApi<{ visit: { checkedInAt: string } }>(
        user,
        `/appointments/${created.visitOrder}`,
      );
      expect(prior.visit.checkedInAt).toBe(`${config.date}T13:40:00`);
      // 同一人有效预约不能重复；关闭的未来场次可重新下单，确认序号不会复用。
      created.unpaidOrder = await createBooking(user, 2);
      await user.getByRole("button", { name: "取消预约" }).click();
      await expect(
        user.getByRole("heading", { name: "预约已取消", exact: true }),
      ).toBeVisible();
    });
    const doctorContext = await browser.newContext(),
      doctor = await doctorContext.newPage();
    monitor(doctor);
    await test.step("医生只读详情、开始与完成；用户端事实一致", async () => {
      await login(doctor, config.actors.doctor);
      await doctor.goto("/doctor");
      await doctor.getByLabel("日期", { exact: true }).fill(config.date);
      await doctor.getByRole("button", { name: /下午第一段/ }).click();
      const row = doctor.locator("tbody tr").filter({ hasText: "联调就诊人" });
      await expect(row).toContainText("候诊中");
      await expect(row.getByRole("button", { name: "开始接诊" })).toBeEnabled();
      await expect(
        row.getByRole("button", { name: "完成接诊" }),
      ).toBeDisabled();
      await row.getByRole("button", { name: "查看", exact: true }).click();
      await expect(doctor.locator(".doctor-detail")).toContainText("13:40:00");
      await doctor.getByRole("button", { name: "关闭详情" }).click();
      await row.getByRole("button", { name: "开始接诊" }).click();
      await expect(row).toContainText("就诊中");
      await user.goto(`/appointments/${created.visitOrder}`);
      await expect(
        user.getByRole("heading", { name: "预约已确认 · 就诊中", exact: true }),
      ).toBeVisible();
      await row.getByRole("button", { name: "完成接诊" }).click();
      await expect(row).toContainText("已就诊");
      await user.getByRole("button", { name: "刷新进度" }).click();
      await expect(
        user.getByRole("heading", { name: "预约已确认 · 已就诊", exact: true }),
      ).toBeVisible();
      const u = await readApi<{
        visit: {
          status: string;
          checkedInAt: string;
          startedAt: string;
          completedAt: string;
        };
      }>(user, `/appointments/${created.visitOrder}`);
      const d = await readApi<{
        visit: {
          status: string;
          checkedInAt: string;
          startedAt: string;
          completedAt: string;
        };
      }>(doctor, `/doctor/appointments/${created.visitOrder}`);
      expect(u.visit.status).toBe("COMPLETED");
      expect(d.visit.status).toBe(u.visit.status);
      for (const key of ["checkedInAt", "startedAt", "completedAt"] as const)
        expect(d.visit[key]).toBe(u.visit[key]);
      await doctor.screenshot({
        path: path.join(config.evidence, "doctor-completed.png"),
        fullPage: true,
      });
    });
    await test.step("设置快照、列表排序和真实权限拒绝", async () => {
      await page.goto("/admin/booking-settings");
      await page.getByLabel("每场容量").fill("6");
      await page.getByLabel("挂号费（元）").fill("15.00");
      await page.getByRole("button", { name: "核对并保存设置" }).click();
      await expect(page.getByRole("status")).toHaveText("统一预约设置已保存。");
      const schedules = await readApi<
        {
          appointmentDate: string;
          sessionType: string;
          registrationFee: string;
          totalCapacity: number;
        }[]
      >(user, `/doctors/${created.doctor}/schedules`);
      const current = schedules.find(
        (s) =>
          s.appointmentDate === config.date &&
          s.sessionType === "AFTERNOON_FIRST",
      )!;
      expect(current.registrationFee).toBe("12.34");
      expect(current.totalCapacity).toBe(4);
      const notGenerated = schedules.find(
        (s) =>
          s.appointmentDate === config.date &&
          s.sessionType === "AFTERNOON_SECOND",
      )!;
      expect(notGenerated.registrationFee).toBe("15.00");
      expect(notGenerated.totalCapacity).toBe(6);
      await user.goto("/appointments");
      await expect(user.locator("article")).toHaveCount(3);
      await user.getByLabel("就诊时间").selectOption("ASC");
      await expect(user.locator("article").first()).toContainText(config.date);
      await user.goto("/admin/catalog");
      await expect(
        user.getByRole("heading", { name: "无权访问此页面" }),
      ).toBeVisible();
      const forbidden = await user.evaluate(async () => {
        const s = JSON.parse(sessionStorage.getItem("appoint.session.v1")!);
        return (
          await fetch("/api/v1/admin/booking-settings", {
            headers: { Authorization: "Bearer " + s.token },
          })
        ).status;
      });
      expect(forbidden).toBe(403);
    });
    expect(errors).toEqual([]);
    await userContext.close();
    await doctorContext.close();
    writeFileSync(
      path.join(config.evidence, "created-records.json"),
      JSON.stringify(created, null, 2) + "\n",
    );
  } finally {
    await Promise.allSettled(tasks);
    writeFileSync(
      path.join(config.evidence, "http-summary.json"),
      JSON.stringify(records, null, 2) + "\n",
    );
    writeFileSync(
      path.join(config.evidence, "page-errors.json"),
      JSON.stringify(errors, null, 2) + "\n",
    );
    await info.attach("real-http-summary", {
      body: JSON.stringify(records, null, 2),
      contentType: "application/json",
    });
  }
});
