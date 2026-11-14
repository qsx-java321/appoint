import { effectScope, ref } from "vue";
import { afterEach, expect, it, vi } from "vitest";
import { useRequest } from "../../src/composables/useRequest";
import { usePolling } from "../../src/composables/usePolling";
afterEach(() => vi.useRealTimers());
it("较旧查询不能覆盖新选择结果", async () => {
  const state = useRequest("initial");
  let finish!: (value: string) => void;
  const first = state.run(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  await state.run(async () => "new");
  finish("old");
  await first;
  expect(state.data.value).toBe("new");
});
it("异步查询30次后停止，不无限请求", async () => {
  vi.useFakeTimers();
  Object.defineProperty(document, "hidden", {
    value: false,
    configurable: true,
  });
  const scope = effectScope(),
    refresh = vi.fn(async () => true);
  const polling = scope.run(() => usePolling(ref(true), refresh))!;
  await vi.advanceTimersByTimeAsync(70000);
  expect(refresh).toHaveBeenCalledTimes(30);
  expect(polling.exhausted.value).toBe(true);
  scope.stop();
});
it("离开页面后未完成查询不会重新启动计时器", async () => {
  vi.useFakeTimers();
  Object.defineProperty(document, "hidden", {
    value: false,
    configurable: true,
  });
  let finish!: (value: boolean) => void;
  const refresh = vi.fn(
      () =>
        new Promise<boolean>((resolve) => {
          finish = resolve;
        }),
    ),
    scope = effectScope();
  scope.run(() => usePolling(ref(true), refresh));
  await vi.advanceTimersByTimeAsync(2000);
  scope.stop();
  finish(true);
  await vi.advanceTimersByTimeAsync(10000);
  expect(refresh).toHaveBeenCalledTimes(1);
});
