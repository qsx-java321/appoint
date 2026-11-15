# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: workflow.spec.ts >> 真实Compose：管理目录与绑定、支付失败重试、退款、签到接诊、双端一致和权限
- Location: .local-env/workflow-browser/workflow.spec.ts:105:1

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: locator('.doctor-detail')
Expected substring: "13:40:00"
Received string:    "联调就诊人 · 就诊详情关闭详情预约单号APfdc1a7ade48d4d848f63e45e58fe6620就诊序号001身份证110105********7319日期场次2026-11-15 14:00–16:00医院医生联调医院-20261115-132151 · 联调科室 · 联调医生当前状态候诊中签到时间2026-11-15 13:40:24开始接诊—完成接诊—"
Timeout: 5000ms

Call log:
  - Expect "toContainText" locator('.doctor-detail') with timeout 5000ms
  - waiting for locator('.doctor-detail')
    13 × locator resolved to <aside class="panel doctor-detail">…</aside>
       - unexpected value "联调就诊人 · 就诊详情关闭详情预约单号APfdc1a7ade48d4d848f63e45e58fe6620就诊序号001身份证110105********7319日期场次2026-11-15 14:00–16:00医院医生联调医院-20261115-132151 · 联调科室 · 联调医生当前状态候诊中签到时间2026-11-15 13:40:24开始接诊—完成接诊—"

```

```yaml
- complementary:
  - heading "联调就诊人 · 就诊详情" [level=2]
  - button "关闭详情"
  - term: 预约单号
  - definition: APfdc1a7ade48d4d848f63e45e58fe6620
  - term: 就诊序号
  - definition: "001"
  - term: 身份证
  - definition: 110105********7319
  - term: 日期场次
  - definition: 2026-11-15 14:00–16:00
  - term: 医院医生
  - definition: 联调医院-20261115-132151 · 联调科室 · 联调医生
  - term: 当前状态
  - definition: 候诊中
  - term: 签到时间
  - definition: 2026-11-15 13:40:24
  - term: 开始接诊
  - definition: —
  - term: 完成接诊
  - definition: —
```

# Test source

```ts
  223 |       await user.getByRole("button", { name: "注册账号", exact: true }).click();
  224 |       await expect(
  225 |         user.getByRole("heading", { name: "欢迎登录" }),
  226 |       ).toBeVisible();
  227 |       await login(user, config.actors.user);
  228 |       await user.goto("/patients");
  229 |       await user.getByRole("button", { name: "新增就诊人" }).click();
  230 |       await user.getByLabel("姓名", { exact: true }).fill("联调就诊人");
  231 |       await user
  232 |         .getByLabel("身份证号码", { exact: true })
  233 |         .fill(config.documentNo);
  234 |       await user.getByLabel("联系电话").fill("13800138000");
  235 |       await user.getByLabel("联系邮箱").fill("patient@example.com");
  236 |       await user.getByRole("button", { name: "保存就诊人" }).click();
  237 |       await expect(
  238 |         user.getByRole("button", { name: "编辑资料" }),
  239 |       ).toBeVisible();
  240 |       await user.getByRole("button", { name: "编辑资料" }).click();
  241 |       await user.getByLabel("联系电话").fill("13900139000");
  242 |       await user.getByRole("button", { name: "保存就诊人" }).click();
  243 |       await user.getByRole("button", { name: "停用", exact: true }).click();
  244 |       await expect(
  245 |         user.getByRole("button", { name: "启用", exact: true }),
  246 |       ).toBeVisible();
  247 |       await user.getByRole("button", { name: "启用", exact: true }).click();
  248 |       await expect(
  249 |         user.getByRole("button", { name: "停用", exact: true }),
  250 |       ).toBeVisible();
  251 |     });
  252 |     await test.step("未来场次支付失败重试、真实异步确认与取消退款", async () => {
  253 |       created.refundOrder = await createBooking(user, 2);
  254 |       await expect(user.locator(".facts").last()).toContainText("12.34");
  255 |       await payAndConfirm(user, true);
  256 |       const details = await readApi<{
  257 |         paymentAttemptCount: number;
  258 |         visitNumber: number;
  259 |         canCancel: boolean;
  260 |       }>(user, `/appointments/${created.refundOrder}`);
  261 |       expect(details.paymentAttemptCount).toBe(2);
  262 |       expect(details.visitNumber).toBe(1);
  263 |       expect(details.canCancel).toBe(true);
  264 |       const cancel = user.waitForResponse(
  265 |         (r) => r.url().endsWith("/cancel") && r.request().method() === "POST",
  266 |       );
  267 |       await user.getByRole("button", { name: "取消预约" }).click();
  268 |       expect((await cancel).status()).toBe(202);
  269 |       await expect(
  270 |         user.getByRole("heading", {
  271 |           name: "预约已取消 · 退款已完成",
  272 |           exact: true,
  273 |         }),
  274 |       ).toBeVisible({ timeout: 45000 });
  275 |       const result = await readApi<{
  276 |         refund: { status: string; completedAt: string };
  277 |         paymentStatus: string;
  278 |       }>(user, `/appointments/${created.refundOrder}`);
  279 |       expect(result.paymentStatus).toBe("REFUNDED");
  280 |       expect(result.refund.completedAt).toBeTruthy();
  281 |       await user.screenshot({
  282 |         path: path.join(config.evidence, "refund-completed.png"),
  283 |         fullPage: true,
  284 |       });
  285 |     });
  286 |     await test.step("当天场次支付、签到与未支付取消", async () => {
  287 |       created.visitOrder = await createBooking(user, 0);
  288 |       await payAndConfirm(user);
  289 |       await expect(user.getByRole("button", { name: "取消预约" })).toHaveCount(
  290 |         0,
  291 |       );
  292 |       await user.getByRole("button", { name: "到院签到", exact: true }).click();
  293 |       await expect(
  294 |         user.getByRole("heading", { name: "预约已确认 · 候诊中", exact: true }),
  295 |       ).toBeVisible();
  296 |       const prior = await readApi<{ visit: { checkedInAt: string } }>(
  297 |         user,
  298 |         `/appointments/${created.visitOrder}`,
  299 |       );
  300 |       expect(prior.visit.checkedInAt.startsWith(`${config.date}T13:40:`)).toBe(true);
  301 |       // 同一人有效预约不能重复；关闭的未来场次可重新下单，确认序号不会复用。
  302 |       created.unpaidOrder = await createBooking(user, 2);
  303 |       await user.getByRole("button", { name: "取消预约" }).click();
  304 |       await expect(
  305 |         user.getByRole("heading", { name: "预约已取消", exact: true }),
  306 |       ).toBeVisible();
  307 |     });
  308 |     const doctorContext = await browser.newContext(),
  309 |       doctor = await doctorContext.newPage();
  310 |     monitor(doctor);
  311 |     await test.step("医生只读详情、开始与完成；用户端事实一致", async () => {
  312 |       await login(doctor, config.actors.doctor);
  313 |       await doctor.goto("/doctor");
  314 |       await doctor.getByLabel("日期", { exact: true }).fill(config.date);
  315 |       await doctor.getByRole("button", { name: /下午第一段/ }).click();
  316 |       const row = doctor.locator("tbody tr").filter({ hasText: "联调就诊人" });
  317 |       await expect(row).toContainText("候诊中");
  318 |       await expect(row.getByRole("button", { name: "开始接诊" })).toBeEnabled();
  319 |       await expect(
  320 |         row.getByRole("button", { name: "完成接诊" }),
  321 |       ).toBeDisabled();
  322 |       await row.getByRole("button", { name: "查看", exact: true }).click();
> 323 |       await expect(doctor.locator(".doctor-detail")).toContainText("13:40:00");
      |                                                      ^ Error: expect(locator).toContainText(expected) failed
  324 |       await doctor.getByRole("button", { name: "关闭详情" }).click();
  325 |       await row.getByRole("button", { name: "开始接诊" }).click();
  326 |       await expect(row).toContainText("就诊中");
  327 |       await user.goto(`/appointments/${created.visitOrder}`);
  328 |       await expect(
  329 |         user.getByRole("heading", { name: "预约已确认 · 就诊中", exact: true }),
  330 |       ).toBeVisible();
  331 |       await row.getByRole("button", { name: "完成接诊" }).click();
  332 |       await expect(row).toContainText("已就诊");
  333 |       await user.getByRole("button", { name: "刷新进度" }).click();
  334 |       await expect(
  335 |         user.getByRole("heading", { name: "预约已确认 · 已就诊", exact: true }),
  336 |       ).toBeVisible();
  337 |       const u = await readApi<{
  338 |         visit: {
  339 |           status: string;
  340 |           checkedInAt: string;
  341 |           startedAt: string;
  342 |           completedAt: string;
  343 |         };
  344 |       }>(user, `/appointments/${created.visitOrder}`);
  345 |       const d = await readApi<{
  346 |         visit: {
  347 |           status: string;
  348 |           checkedInAt: string;
  349 |           startedAt: string;
  350 |           completedAt: string;
  351 |         };
  352 |       }>(doctor, `/doctor/appointments/${created.visitOrder}`);
  353 |       expect(u.visit.status).toBe("COMPLETED");
  354 |       expect(d.visit.status).toBe(u.visit.status);
  355 |       for (const key of ["checkedInAt", "startedAt", "completedAt"] as const)
  356 |         expect(d.visit[key]).toBe(u.visit[key]);
  357 |       await doctor.screenshot({
  358 |         path: path.join(config.evidence, "doctor-completed.png"),
  359 |         fullPage: true,
  360 |       });
  361 |     });
  362 |     await test.step("设置快照、列表排序和真实权限拒绝", async () => {
  363 |       await page.goto("/admin/booking-settings");
  364 |       await page.getByLabel("每场容量").fill("6");
  365 |       await page.getByLabel("挂号费（元）").fill("15.00");
  366 |       await page.getByRole("button", { name: "核对并保存设置" }).click();
  367 |       await expect(page.getByRole("status")).toHaveText("统一预约设置已保存。");
  368 |       const schedules = await readApi<
  369 |         {
  370 |           appointmentDate: string;
  371 |           sessionType: string;
  372 |           registrationFee: string;
  373 |           totalCapacity: number;
  374 |         }[]
  375 |       >(user, `/doctors/${created.doctor}/schedules`);
  376 |       const current = schedules.find(
  377 |         (s) =>
  378 |           s.appointmentDate === config.date &&
  379 |           s.sessionType === "AFTERNOON_FIRST",
  380 |       )!;
  381 |       expect(current.registrationFee).toBe("12.34");
  382 |       expect(current.totalCapacity).toBe(4);
  383 |       const notGenerated = schedules.find(
  384 |         (s) =>
  385 |           s.appointmentDate === config.date &&
  386 |           s.sessionType === "AFTERNOON_SECOND",
  387 |       )!;
  388 |       expect(notGenerated.registrationFee).toBe("15.00");
  389 |       expect(notGenerated.totalCapacity).toBe(6);
  390 |       await user.goto("/appointments");
  391 |       await expect(user.locator("article")).toHaveCount(3);
  392 |       await user.getByLabel("就诊时间").selectOption("ASC");
  393 |       await expect(user.locator("article").first()).toContainText(config.date);
  394 |       await user.goto("/admin/catalog");
  395 |       await expect(
  396 |         user.getByRole("heading", { name: "无权访问此页面" }),
  397 |       ).toBeVisible();
  398 |       const forbidden = await user.evaluate(async () => {
  399 |         const s = JSON.parse(sessionStorage.getItem("appoint.session.v1")!);
  400 |         return (
  401 |           await fetch("/api/v1/admin/booking-settings", {
  402 |             headers: { Authorization: "Bearer " + s.token },
  403 |           })
  404 |         ).status;
  405 |       });
  406 |       expect(forbidden).toBe(403);
  407 |     });
  408 |     expect(errors).toEqual([]);
  409 |     await userContext.close();
  410 |     await doctorContext.close();
  411 |     writeFileSync(
  412 |       path.join(config.evidence, "created-records.json"),
  413 |       JSON.stringify(created, null, 2) + "\n",
  414 |     );
  415 |   } finally {
  416 |     await Promise.allSettled(tasks);
  417 |     writeFileSync(
  418 |       path.join(config.evidence, "http-summary.json"),
  419 |       JSON.stringify(records, null, 2) + "\n",
  420 |     );
  421 |     writeFileSync(
  422 |       path.join(config.evidence, "page-errors.json"),
  423 |       JSON.stringify(errors, null, 2) + "\n",
```