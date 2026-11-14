import type { Appointment, Schedule } from "../src/api/types";
export const hospital = {
  id: "9007199254740999",
  name: "测试医院",
  address: "测试地址100号",
};
export const department = { id: "11", name: "心内科" };
export const doctor = {
  id: "101",
  doctorNo: "DOC000101",
  name: "李明",
  title: "主任医师",
  introduction: "测试医生简介",
};
export const patient = {
  id: "201",
  name: "测试就诊人",
  documentType: "RESIDENT_ID_CARD" as const,
  documentNoMasked: "1101**********002X",
  contactPhone: "13800138000",
  contactEmail: "patient@example.com",
  enabled: true,
  createdAt: "2026-11-14T09:00:00",
  updatedAt: "2026-11-14T09:00:00",
};
export const login = {
  accessToken: "test-token",
  tokenType: "Bearer",
  expiresIn: 7200,
  user: { id: "12", email: "test@example.com", roles: ["USER"] },
};
export function schedule(overrides: Partial<Schedule> = {}): Schedule {
  return {
    doctorId: doctor.id,
    doctorNo: doctor.doctorNo,
    appointmentDate: "2026-11-16",
    sessionType: "AFTERNOON_FIRST",
    startTime: "14:00:00",
    endTime: "16:00:00",
    totalCapacity: 20,
    availableCapacity: 20,
    registrationFee: "10.00",
    feeCurrency: "CNY",
    bookable: true,
    bookingClosesAt: "2026-11-16T13:45:00",
    availability: "AVAILABLE",
    serverTime: "2026-11-14T09:40:00",
    ...overrides,
  };
}
export function appointment(overrides: Partial<Appointment> = {}): Appointment {
  return {
    id: "301",
    appointmentNo: "TEST-AP301",
    scheduleId: "401",
    appointmentDate: "2026-11-16",
    startTime: "14:00:00",
    endTime: "16:00:00",
    status: "PENDING_PAYMENT",
    visitNumber: null,
    createdAt: "2026-11-14T09:40:00",
    paymentOrderId: "501",
    paymentStatus: "UNPAID",
    workflowStatus: "WAITING_FOR_PAYMENT",
    amount: 10,
    currency: "CNY",
    paymentAttemptCount: 0,
    latestPaymentAttempt: null,
    refund: null,
    paymentExpiresAt: "2026-11-14T09:55:00",
    closedAt: null,
    serverTime: "2026-11-14T09:40:00",
    paymentExpired: false,
    canPay: true,
    canCancel: true,
    patient,
    doctor: { ...doctor, department, hospital },
    sessionType: "AFTERNOON_FIRST",
    visit: null,
    ...overrides,
  };
}
