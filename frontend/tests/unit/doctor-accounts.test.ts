import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createApp, nextTick, type App } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";
import DoctorAccountsView from "../../src/views/DoctorAccountsView.vue";
import { api } from "../../src/api";
import type { DoctorAccountList } from "../../src/api/types";

const data: DoctorAccountList = {
  environment: "test",
  items: [
    {
      hospitalId: "1",
      hospitalName: "第一医院",
      departmentId: "2",
      departmentName: "内科",
      doctorId: "3",
      doctorNo: "DOC000003",
      doctorName: "张医生",
      userId: "9007199254740999",
      email: "test-doctor-001@example.local",
      bindingStatus: "BOUND",
      passwordHint: "初始密码见 .env.test 的 LOCAL_SEED_PASSWORD",
    },
    {
      hospitalId: "4",
      hospitalName: "第二医院",
      departmentId: "5",
      departmentName: "骨科",
      doctorId: "6",
      doctorNo: "DOC000006",
      doctorName: "未绑定医生",
      userId: null,
      email: null,
      bindingStatus: "UNBOUND",
      passwordHint: "未绑定账号",
    },
  ],
};
let app: App;
let root: HTMLDivElement;
beforeEach(() => {
  root = document.createElement("div");
  document.body.append(root);
});
afterEach(() => {
  app?.unmount();
  root.remove();
});
async function mount() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: DoctorAccountsView },
      {
        path: "/admin/doctor-accounts/bind",
        component: { template: "<p>首次绑定</p>" },
      },
    ],
  });
  await router.push("/");
  app = createApp(DoctorAccountsView);
  app.use(router).mount(root);
  await nextTick();
}

it("展示环境、真实对应关系、长ID、密码说明与未绑定医生，并提供绑定入口", async () => {
  vi.spyOn(api, "doctorAccounts").mockResolvedValue(data);
  await mount();
  await vi.waitFor(() =>
    expect(root.querySelectorAll("tbody tr")).toHaveLength(2),
  );
  for (const text of [
    "当前环境：test",
    "第一医院",
    "内科",
    "张医生",
    "9007199254740999",
    "test-doctor-001@example.local",
    ".env.test",
    "第二医院",
    "骨科",
    "未绑定医生",
    "未绑定账号",
    "修改配置不会更新已有账号密码",
  ])
    expect(root.textContent).toContain(text);
  expect(root.querySelector("a")?.getAttribute("href")).toBe(
    "/admin/doctor-accounts/bind",
  );
});

it("查询失败后可以重试并恢复列表", async () => {
  const load = vi
    .spyOn(api, "doctorAccounts")
    .mockRejectedValueOnce(new Error("查询暂时失败"))
    .mockResolvedValue(data);
  await mount();
  await vi.waitFor(() =>
    expect(root.querySelector('[role="alert"]')).not.toBeNull(),
  );
  const retry = root.querySelector(
    '[role="alert"] button',
  ) as HTMLButtonElement;
  retry.click();
  await vi.waitFor(() =>
    expect(root.querySelectorAll("tbody tr")).toHaveLength(2),
  );
  expect(load).toHaveBeenCalledTimes(2);
});

it("空目录展示空状态，刷新后显示新增医生", async () => {
  vi.spyOn(api, "doctorAccounts")
    .mockResolvedValueOnce({ environment: "test", items: [] })
    .mockResolvedValue(data);
  await mount();
  await vi.waitFor(() => expect(root.textContent).toContain("暂无医生档案"));
  (root.querySelector("button") as HTMLButtonElement).click();
  await vi.waitFor(() =>
    expect(root.querySelectorAll("tbody tr")).toHaveLength(2),
  );
});
