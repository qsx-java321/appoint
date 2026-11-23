import type { Appointment, Money, VisitStatus } from "../api/types";
export const sessionNames: Record<string, string> = {
  MORNING_FIRST: "上午第一段",
  MORNING_SECOND: "上午第二段",
  AFTERNOON_FIRST: "下午第一段",
  AFTERNOON_SECOND: "下午第二段",
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
  if (a.status === "CANCELLED") return "预约已取消";
  if (a.paymentExpired || a.status === "EXPIRED") return "预约已过期";
  if (a.status === "CONFIRMED")
    return "预约已确认 · " + (a.visit ? visitNames[a.visit.status] : "待签到");
  if (a.workflowStatus === "WAITING_FOR_PAYMENT") return "待支付";
  return "请刷新确认状态";
}

export function withinPaymentDeadline(now: number, deadline: string | null) {
  return deadline != null && Math.floor(now / 1000) <= epoch(deadline) / 1000;
}
