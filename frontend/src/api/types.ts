export type Id = string;
export type Role = "USER" | "DOCTOR" | "ADMIN";
export type Money = string | number;
export interface RegistrationCodeResponse {
  expiresIn: number;
}
export interface SignupTicketResponse {
  signupTicket: string;
  expiresIn: number;
}
export interface User {
  id: Id;
  email: string;
  roles: Role[];
}
export interface Login {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}
export interface Hospital {
  id: Id;
  name: string;
  address: string;
}
export interface Department {
  id: Id;
  name: string;
}
export interface Doctor {
  id: Id;
  doctorNo: string;
  name: string;
  title: string;
  introduction: string | null;
}
export interface DoctorAccount {
  hospitalId: Id;
  hospitalName: string;
  departmentId: Id;
  departmentName: string;
  doctorId: Id;
  doctorNo: string;
  doctorName: string;
  userId: Id | null;
  email: string | null;
  bindingStatus: "BOUND" | "UNBOUND";
  passwordHint: string;
}
export interface DoctorAccountList {
  environment: string;
  items: DoctorAccount[];
}
export type SessionType =
  "MORNING_FIRST" | "MORNING_SECOND" | "AFTERNOON_FIRST" | "AFTERNOON_SECOND";
export interface Schedule {
  doctorId: Id;
  doctorNo: string;
  appointmentDate: string;
  sessionType: SessionType;
  startTime: string;
  endTime: string;
  totalCapacity: number;
  availableCapacity: number;
  registrationFee: string;
  feeCurrency: string;
  bookable: boolean;
  bookingClosesAt: string;
  availability: "AVAILABLE" | "FULL" | "STARTED" | "CUTOFF_REACHED";
  serverTime: string;
}
export interface PatientInput {
  name: string;
  documentType: "RESIDENT_ID_CARD";
  documentNo: string;
  contactPhone: string;
  contactEmail: string;
}
export interface Patient extends Omit<PatientInput, "documentNo"> {
  id: Id;
  documentNoMasked: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface BookingInput {
  doctorId: Id;
  appointmentDate: string;
  sessionType: SessionType;
  patientId: Id;
}
export interface BookingToken {
  token: string;
  expiresIn: number;
}
export type CreateBooking =
  | { pending: true }
  | {
      appointmentOrderId: Id;
      appointmentNo: string;
      appointmentStatus: string;
      paymentOrderId: Id;
      paymentNo: string;
      paymentStatus: string;
      amount: string;
      currency: string;
      createdAt: string;
      paymentExpiresAt: string;
      serverTime: string;
    };
export type VisitStatus =
  "NOT_CHECKED_IN" | "WAITING" | "IN_PROGRESS" | "COMPLETED";
export interface Visit {
  status: VisitStatus;
  checkedInAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  canCheckIn: boolean;
  checkInOpensAt: string;
  checkInClosesAt: string;
}
export interface PatientSnapshot extends Omit<
  Patient,
  "enabled" | "createdAt" | "updatedAt"
> {}
export interface DoctorSnapshot extends Omit<Doctor, "introduction"> {
  department: Department;
  hospital: Hospital;
}
export interface Appointment {
  id: Id;
  appointmentNo: string;
  scheduleId: Id;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  status: string;
  visitNumber: number | null;
  createdAt: string;
  paymentOrderId: Id;
  paymentStatus: string;
  workflowStatus: string | null;
  amount: Money;
  currency: string;
  paidAt: string | null;
  paymentExpiresAt: string | null;
  closedAt: string | null;
  serverTime: string;
  paymentExpired: boolean;
  canPay: boolean;
  canCancel: boolean;
  patient: PatientSnapshot;
  doctor: DoctorSnapshot;
  sessionType: string | null;
  visit: Visit | null;
}
export interface DoctorSession {
  appointmentDate: string;
  sessionType: SessionType;
  startTime: string;
  endTime: string;
  confirmedCount: number;
  notCheckedInCount: number;
  waitingCount: number;
  inProgressCount: number;
  completedCount: number;
}
export interface DoctorRow {
  appointmentId: Id;
  visitNumber: number;
  patientName: string;
  visitStatus: VisitStatus;
  checkedInAt: string | null;
  canStartVisit: boolean;
  canCompleteVisit: boolean;
}
export interface DoctorDetail {
  appointmentId: Id;
  appointmentNo: string;
  visitNumber: number;
  appointmentDate: string;
  sessionType: string;
  startTime: string;
  endTime: string;
  patient: {
    id: Id;
    name: string;
    documentType: string;
    documentNoMasked: string;
  };
  doctor: DoctorSnapshot;
  visit: Omit<Visit, "canCheckIn" | "checkInOpensAt" | "checkInClosesAt">;
}
export interface BookingSettings {
  capacityPerSession: number;
  registrationFee: string;
  currency: string;
  updatedAt: string;
}
export type NotificationType =
  "APPOINTMENT_CONFIRMED" | "CHECKED_IN" | "VISIT_STARTED" | "VISIT_COMPLETED";
export interface NotificationSummary {
  id: Id;
  type: NotificationType;
  title: string;
  patientName: string;
  occurredAt: string;
}
export interface NotificationDetail extends NotificationSummary {
  detail: string;
  appointmentId: Id;
}
