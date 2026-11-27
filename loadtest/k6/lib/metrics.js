import { Counter, Trend } from 'k6/metrics';

export const businessErrors = new Counter('read_business_error');

export const trends = {
  schedules: new Trend('read_schedules_ms'),
  hospitals: new Trend('read_hospitals_ms'),
  departments: new Trend('read_departments_ms'),
  doctors: new Trend('read_doctors_ms'),
  appointment: new Trend('read_appointment_ms'),
  myAppointments: new Trend('read_my_appointments_ms'),
  notifications: new Trend('read_notifications_ms'),
};
