import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createApp, nextTick, type App } from "vue";
import { createPinia, setActivePinia } from "pinia";
import AccountView from "../../src/views/AccountView.vue";
import { api } from "../../src/api";
import { writeSession } from "../../src/lib/session";
import { useAuth } from "../../src/stores/auth";
import type { User } from "../../src/api/types";

let app: App, root: HTMLDivElement;
const tick = async () => {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await nextTick();
};
const user = (avatarUrl: string | null): User => ({
  id: "12",
  email: "reader@example.com",
  roles: ["USER"],
  avatarUrl,
});

beforeEach(() => {
  sessionStorage.clear();
  root = document.createElement("div");
  document.body.append(root);
  writeSession({
    accessToken: "token",
    tokenType: "Bearer",
    expiresIn: 7200,
    user: user(null),
  });
  vi.stubGlobal("URL", {
    ...URL,
    createObjectURL: vi.fn(() => "blob:preview"),
    revokeObjectURL: vi.fn(),
  });
});
afterEach(() => {
  app?.unmount();
  root.remove();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

async function mount() {
  const pinia = createPinia();
  setActivePinia(pinia);
  vi.spyOn(api, "me").mockResolvedValue(
    user("/api/v1/users/12/avatar?v=old.png"),
  );
  app = createApp(AccountView);
  app.use(pinia).mount(root);
  await tick();
}

function selectFile() {
  const input = root.querySelector<HTMLInputElement>("#avatar-file")!;
  const file = new File([new Uint8Array([1, 2, 3])], "avatar.png", {
    type: "image/png",
  });
  Object.defineProperty(input, "files", { value: [file] });
  input.dispatchEvent(new Event("change", { bubbles: true }));
  return file;
}

async function submit() {
  root
    .querySelector("form")!
    .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
  await tick();
}

it("展示当前头像，上传后更新会话并支持删除", async () => {
  const upload = vi
    .spyOn(api, "uploadAvatar")
    .mockResolvedValue(user("/api/v1/users/12/avatar?v=new.png"));
  const remove = vi.spyOn(api, "removeAvatar").mockResolvedValue(user(null));
  await mount();

  expect(
    root
      .querySelector<HTMLImageElement>("img.profile-avatar")
      ?.getAttribute("src"),
  ).toBe("/api/v1/users/12/avatar?v=old.png");

  const file = selectFile();
  await tick();
  expect(root.textContent).toContain("已选择：avatar.png");
  await submit();

  expect(upload).toHaveBeenCalledWith(file);
  expect(
    root
      .querySelector<HTMLImageElement>("img.profile-avatar")
      ?.getAttribute("src"),
  ).toBe("/api/v1/users/12/avatar?v=new.png");
  expect(root.textContent).toContain("头像已更新。");
  expect(useAuth().session?.user.avatarUrl).toBe(
    "/api/v1/users/12/avatar?v=new.png",
  );

  const deleteButton = [...root.querySelectorAll("button")].find(
    (button) => button.textContent?.trim() === "删除头像",
  )!;
  deleteButton.click();
  await tick();

  expect(remove).toHaveBeenCalledOnce();
  expect(root.querySelector("img.profile-avatar")).toBeNull();
  expect(root.textContent).toContain("头像已删除。");
  expect(useAuth().session?.user.avatarUrl).toBeNull();
});

it("超大文件在请求前被拒绝", async () => {
  const upload = vi.spyOn(api, "uploadAvatar");
  await mount();
  const input = root.querySelector<HTMLInputElement>("#avatar-file")!;
  const file = new File([new Uint8Array(2 * 1024 * 1024 + 1)], "large.png", {
    type: "image/png",
  });
  Object.defineProperty(input, "files", { value: [file] });
  input.dispatchEvent(new Event("change", { bubbles: true }));
  await tick();

  expect(root.textContent).toContain("图片不能超过 2 MB。");
  await submit();
  expect(upload).not.toHaveBeenCalled();
});
