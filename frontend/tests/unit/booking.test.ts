import { beforeEach, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useBooking } from "../../src/stores/booking";
import { writeSession } from "../../src/lib/session";
import { api } from "../../src/api";
import { ApiError } from "../../src/api/client";
import { hospital, department, doctor, schedule } from "../fixtures";
import type { CreateBooking } from "../../src/api/types";

const receipt = {
  appointmentOrderId: "301",
  appointmentNo: "AP301",
  appointmentStatus: "PENDING_PAYMENT",
  paymentOrderId: "501",
  paymentNo: "PAY501",
  paymentStatus: "UNPAID",
  amount: "10.00",
  currency: "CNY",
  createdAt: "2026-11-22T10:00:00",
  paymentExpiresAt: "2026-11-22T10:15:00",
  serverTime: "2026-11-22T10:00:00",
} satisfies CreateBooking;

beforeEach(() => {
  vi.restoreAllMocks();
  sessionStorage.clear();
  setActivePinia(createPinia());
  writeSession({
    accessToken: "token",
    tokenType: "Bearer",
    expiresIn: 7200,
    user: { id: "12", email: "a@example.com", roles: ["USER"] },
  });
  vi.spyOn(api, "bookingToken").mockResolvedValue({
    token: "issued-token",
    expiresIn: 600,
  });
});
it("断网及202后仍复用确认页领到的令牌和原请求，成功重放后清理", async () => {
  const store = useBooking();
  store.selection = { hospital, department, doctor, schedule: schedule() };
  await store.prepare();
  const create = vi
    .spyOn(api, "createAppointment")
    .mockRejectedValueOnce(new ApiError("timeout"))
    .mockResolvedValueOnce({ pending: true })
    .mockResolvedValueOnce(receipt);
  await expect(store.create("201")).rejects.toThrow("timeout");
  expect(store.pending?.key).toBe("issued-token");
  expect(await store.create("different-patient")).toBeNull();
  expect(store.pending?.key).toBe("issued-token");
  expect(await store.create("different-patient")).toBe("301");
  expect(create.mock.calls).toEqual(Array(3).fill(create.mock.calls[0]));
  expect(create.mock.calls[1][0].patientId).toBe("201");
  expect(api.bookingToken).toHaveBeenCalledTimes(1);
  expect(store.pending).toBeNull();
});
it("明确库存冲突后允许改选，不保留失败提交身份", async () => {
  const store = useBooking();
  store.selection = { hospital, department, doctor, schedule: schedule() };
  const create = vi
    .spyOn(api, "createAppointment")
    .mockRejectedValueOnce(new ApiError("已约满", 409, 4002))
    .mockResolvedValueOnce(receipt);
  await expect(store.create("201")).rejects.toThrow("已约满");
  expect(store.pending).toBeNull();
  vi.mocked(api.bookingToken).mockResolvedValueOnce({
    token: "new-token",
    expiresIn: 600,
  });
  await expect(store.create("202")).resolves.toBe("301");
  expect(create.mock.calls[1][1]).toBe("new-token");
});
it("提交中禁止双击并发创建", async () => {
  const store = useBooking();
  store.selection = { hospital, department, doctor, schedule: schedule() };
  let complete!: (value: CreateBooking) => void;
  const create = vi.spyOn(api, "createAppointment").mockImplementation(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  const pending = store.create("201");
  await expect(store.create("201")).rejects.toThrow("正在提交");
  await vi.waitFor(() => expect(create).toHaveBeenCalledTimes(1));
  complete(receipt);
  await pending;
  expect(create).toHaveBeenCalledTimes(1);
});
