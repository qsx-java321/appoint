import type { Appointment, Money, VisitStatus } from "../api/types";
export const refundReasons: Record<string, string> = {
  APPOINTMENT_CANCELLED: "用户取消预约",
  LATE_PAYMENT: "预约关闭后确认收款",
  APPOINTMENT_NOT_CONFIRMABLE: "预约无法确认",
  RECONCILIATION_CLOSED_PAID: "已关闭预约的收款退款补偿",
};
export const sessionNames: Record<string, string> = {
  MORNING_FIRST: "上午第一段",
  MORNING_SECOND: "上午第二段",
  AFTERNOON_FIRST: "下午第一段",
  AFTERNOON_SECOND: "下午第二段",
  MORNING: "上午（历史场次）",
  AFTERNOON: "下午（历史场次）",
};
export const visitNames: Record<VisitStatus, string> = {
  NOT_CHECKED_IN: "待签到",
  WAITING: "候诊中",
  IN_PROGRESS: "就诊中",
  COMPLETED: "已就诊",
};
export const availabilityNames = {
  AVAILABLE: "可预约",
  FULL: "已约满",
  STARTED: "已开诊",
  CUTOFF_REACHED: "预约已截止",
};
export function money(value: Money) {
  return Number(value).toFixed(2);
}
export function time(value: string | null | undefined) {
  return value ? value.replace("T", " ").slice(0, 19) : "—";
}
export function epoch(value: string) {
  return Date.parse(
    /[Zz]|[+-]\d\d:\d\d$/.test(value) ? value : value + "+08:00",
  );
}
export function today() {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Shanghai" }).format(
    new Date(),
  );
}
export function label(a: Appointment): string {
  if (["CANCELLED", "CANCELLED_BY_HOSPITAL", "EXPIRED"].includes(a.status)) {
    const reason =
      a.status === "EXPIRED"
        ? "预约已过期"
        : a.status === "CANCELLED_BY_HOSPITAL"
          ? "医院取消（历史记录）"
          : "预约已取消";
    return (
      reason +
      (a.paymentStatus === "REFUND_PENDING"
        ? " · 退款处理中"
        : a.paymentStatus === "REFUNDED"
          ? " · 退款已完成"
          : "")
    );
  }
  if (a.status === "CONFIRMED")
    return "预约已确认 · " + (a.visit ? visitNames[a.visit.status] : "待签到");
  return (
    (
      {
        WAITING_FOR_PAYMENT: "待支付",
        PAYMENT_PROCESSING: "支付处理中",
        CONFIRMING_APPOINTMENT: "已支付，正在确认预约",
      } as Record<string, string>
    )[a.workflowStatus || ""] || "请刷新确认状态"
  );
}
export function needsPolling(a: Appointment) {
  return [
    "PAYMENT_PROCESSING",
    "CONFIRMING_APPOINTMENT",
    "CANCELLED_REFUNDING",
  ].includes(a.workflowStatus || "");
}
