import { expect, it } from "vitest";
import { appointment } from "../fixtures";
import { label, needsPolling } from "../../src/lib/presentation";
it("收款确认和预约确认分开", () => {
  const a = appointment({
    paymentStatus: "PAID",
    workflowStatus: "CONFIRMING_APPOINTMENT",
  });
  expect(label(a)).toBe("已支付，正在确认预约");
  expect(needsPolling(a)).toBe(true);
});
it("关闭原因和退款进度分开", () => {
  expect(
    label(
      appointment({
        status: "EXPIRED",
        paymentStatus: "REFUND_PENDING",
        workflowStatus: "CANCELLED_REFUNDING",
      }),
    ),
  ).toBe("预约已过期 · 退款处理中");
  expect(
    label(appointment({ status: "CANCELLED", paymentStatus: "REFUNDED" })),
  ).toBe("预约已取消 · 退款已完成");
});
it("已确认未签到不会变成爽约或退款", () => {
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
  expect(needsPolling(a)).toBe(false);
});
