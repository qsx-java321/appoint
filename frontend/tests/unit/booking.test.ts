import { beforeEach, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useBooking } from "../../src/stores/booking";
import { writeSession } from "../../src/lib/session";
import { api } from "../../src/api";
import { ApiError } from "../../src/api/client";
import { hospital, department, doctor, schedule } from "../fixtures";
beforeEach(() => {
  sessionStorage.clear();
  setActivePinia(createPinia());
  writeSession({
    accessToken: "token",
    tokenType: "Bearer",
    expiresIn: 7200,
    user: { id: "12", email: "a@example.com", roles: ["USER"] },
  });
});
it("未知结果重试复用原键原体，200重放只需要预约ID", async () => {
  const store = useBooking();
  store.selection = { hospital, department, doctor, schedule: schedule() };
  const create = vi
    .spyOn(api, "createAppointment")
    .mockRejectedValueOnce(new ApiError("timeout"))
    .mockResolvedValueOnce({
      appointmentOrderId: "301",
      appointmentNo: "AP301",
      replayed: true,
    });
  await expect(store.create("201")).rejects.toThrow("timeout");
  expect(store.pending).not.toBeNull();
  expect(await store.create("different-patient")).toBe("301");
  expect(create.mock.calls[0]).toEqual(create.mock.calls[1]);
  expect(create.mock.calls[1][0].patientId).toBe("201");
  expect(store.pending).toBeNull();
});
it("明确库存冲突后允许改选，不保留失败提交身份", async () => {
  const store = useBooking();
  store.selection = { hospital, department, doctor, schedule: schedule() };
  vi.spyOn(api, "createAppointment").mockRejectedValue(
    new ApiError("已约满", 409, 4002),
  );
  await expect(store.create("201")).rejects.toThrow("已约满");
  expect(store.pending).toBeNull();
});
it("提交中禁止双击并发创建", async () => {
  const store = useBooking();
  store.selection = { hospital, department, doctor, schedule: schedule() };
  let complete!: (value: {
    appointmentOrderId: string;
    appointmentNo: string;
    replayed: true;
  }) => void;
  const create = vi.spyOn(api, "createAppointment").mockImplementation(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  const pending = store.create("201");
  await expect(store.create("201")).rejects.toThrow("正在提交");
  complete({
    appointmentOrderId: "301",
    appointmentNo: "AP301",
    replayed: true,
  });
  await pending;
  expect(create).toHaveBeenCalledTimes(1);
});
