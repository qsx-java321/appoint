import { expect, it } from "vitest";
import { appointment } from "../fixtures";
import {
  epoch,
  label,
  withinPaymentDeadline,
} from "../../src/lib/presentation";
it("最晚支付时刻的最后一秒仍可付款", () => {
  const deadline = "2026-11-19T10:29:59";
  expect(withinPaymentDeadline(epoch(deadline) + 999, deadline)).toBe(true);
  expect(withinPaymentDeadline(epoch(deadline) + 1000, deadline)).toBe(false);
  expect(withinPaymentDeadline(epoch(deadline), null)).toBe(false);
});
it("未支付且超过截止显示为已过期", () => {
  expect(label(appointment({ paymentExpired: true }))).toBe("预约已过期");
});
it("已确认未签到显示待签到", () => {
  const a = appointment({
    status: "CONFIRMED",
    paymentStatus: "PAID",
    workflowStatus: "CONFIRMED",
    visit: {
      status: "NOT_CHECKED_IN",
      checkedInAt: null,
      startedAt: null,
      completedAt: null,
      canCheckIn: false,
      checkInOpensAt: "2026-11-16T13:30:00",
      checkInClosesAt: "2026-11-16T16:00:00",
    },
  });
  expect(label(a)).toBe("预约已确认 · 待签到");
});
