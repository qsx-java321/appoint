import { expect, it } from "vitest";
import { useRequest } from "../../src/composables/useRequest";
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
