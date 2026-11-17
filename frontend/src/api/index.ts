import { request } from "./client";
import type * as T from "./types";

const id = encodeURIComponent;
export const api = {
  login: (email: string, password: string) =>
    request<T.Login>("/auth/login", {
      method: "POST",
      data: { email, password },
    }),
  sendRegistrationCode: (email: string) =>
    request<T.RegistrationCodeResponse>("/auth/register/code", {
      method: "POST",
      data: { email },
    }),
  verifyRegistrationCode: (email: string, code: string) =>
    request<T.SignupTicketResponse>("/auth/register/verify", {
      method: "POST",
      data: { email, code },
    }),
  register: (email: string, password: string, signupTicket: string) =>
    request<T.User>("/auth/register", {
      method: "POST",
      data: { email, password, signupTicket },
    }),
  me: () => request<T.User>("/users/me"),
  hospitals: () => request<T.Hospital[]>("/hospitals"),
  departments: (hospital: string) =>
    request<T.Department[]>(`/hospitals/${id(hospital)}/departments`),
  doctors: (department: string) =>
    request<T.Doctor[]>(`/departments/${id(department)}/doctors`),
  schedules: (doctor: string) =>
    request<T.Schedule[]>(`/doctors/${id(doctor)}/schedules`),
  patients: () => request<T.Patient[]>("/patients"),
  savePatient: (data: Partial<T.PatientInput>, patient?: string) =>
    request<T.Patient>(patient ? `/patients/${id(patient)}` : "/patients", {
      method: patient ? "PATCH" : "POST",
      data,
    }),
  patientStatus: (patient: string, enabled: boolean) =>
    request<T.Patient>(`/patients/${id(patient)}/status`, {
      method: "PATCH",
      data: { enabled },
    }),
  createAppointment: (data: T.BookingInput, key: string) =>
    request<T.CreateBooking>("/appointments", {
      method: "POST",
      data,
      headers: { "Idempotency-Key": key },
    }),
  appointments: (order: string) =>
    request<T.Appointment[]>("/appointments/my", { params: { order } }),
  appointment: (appointment: string) =>
    request<T.Appointment>(`/appointments/${id(appointment)}`),
  cancel: (appointment: string) =>
    request<T.Appointment>(`/appointments/${id(appointment)}/cancel`, {
      method: "POST",
    }),
  checkIn: (appointment: string) =>
    request<T.Appointment>(`/appointments/${id(appointment)}/check-in`, {
      method: "POST",
    }),
  pay: (payment: string) =>
    request<T.PaymentAttempt>(`/payments/${id(payment)}/attempts`, {
      method: "POST",
      data: { channel: "MOCK" },
    }),
  mock: (path: string, outcome: "complete" | "fail") => {
    if (!/^\/api\/v1\/mock-pay\/attempts\/[A-Za-z0-9_-]+$/.test(path))
      throw new Error("模拟支付入口无效，请刷新预约详情。");
    return request<unknown>(path.slice("/api/v1".length) + "/" + outcome, {
      method: "POST",
    });
  },
  sessions: (date: string) =>
    request<T.DoctorSession[]>("/doctor/sessions", { params: { date } }),
  doctorRows: (date: string, sessionType: string) =>
    request<T.DoctorRow[]>("/doctor/appointments", {
      params: { date, sessionType },
    }),
  doctorDetail: (appointment: string) =>
    request<T.DoctorDetail>(`/doctor/appointments/${id(appointment)}`),
  visitAction: (
    appointment: string,
    action: "start-visit" | "complete-visit",
  ) =>
    request<T.DoctorDetail>(
      `/doctor/appointments/${id(appointment)}/${action}`,
      { method: "POST" },
    ),
  settings: () => request<T.BookingSettings>("/admin/booking-settings"),
  saveSettings: (capacityPerSession: number, registrationFee: string) =>
    request<T.BookingSettings>("/admin/booking-settings", {
      method: "PUT",
      data: { capacityPerSession, registrationFee },
    }),
  doctorAccounts: () => request<T.DoctorAccountList>("/admin/doctor-accounts"),
  bind: (doctor: string, userId: string) =>
    request<{ doctorId: string; userId: string }>(
      `/admin/doctors/${id(doctor)}/account`,
      { method: "PUT", data: { userId } },
    ),
  saveCatalog: (
    kind: "hospitals" | "departments" | "doctors",
    target: string | null,
    parent: string,
    data: Record<string, string>,
  ) => {
    const url = target
      ? `/admin/${kind}/${id(target)}`
      : kind === "hospitals"
        ? "/admin/hospitals"
        : kind === "departments"
          ? `/admin/hospitals/${id(parent)}/departments`
          : `/admin/departments/${id(parent)}/doctors`;
    return request<unknown>(url, { method: target ? "PATCH" : "POST", data });
  },
  deleteCatalog: (kind: string, target: string) =>
    request<null>(`/admin/${kind}/${id(target)}`, { method: "DELETE" }),
};
