import type { Page } from "@playwright/test";
import {
  appointment,
  doctor,
  hospital,
  department,
  patient,
  schedule,
  login,
} from "../fixtures";
import type {
  Appointment,
  DoctorRow,
  DoctorSession,
  Patient,
} from "../../src/api/types";
export async function backend(
  page: Page,
  roles = ["USER"],
  authenticated = true,
) {
  const user = { ...login.user, roles };
  if (authenticated)
    await page.addInitScript(
      (value) =>
        sessionStorage.setItem(
          "appoint.session.v1",
          JSON.stringify({
            token: "test-token",
            expiresAt: Date.now() + 7200000,
            user: value,
          }),
        ),
      user,
    );
  const state = {
    hospitals: [{ ...hospital }],
    departments: [{ ...department }],
    doctors: [{ ...doctor }],
    patients: [{ ...patient }] as Patient[],
    appointment: appointment(),
    patientWrites: [] as Record<string, unknown>[],
    keys: [] as string[],
    creates: 0,
    settings: {
      capacityPerSession: 20,
      registrationFee: "10.00",
      currency: "CNY",
      updatedAt: "2026-11-14T09:40:00",
    },
    rows: [
      {
        appointmentId: "1",
        visitNumber: 1,
        patientName: "未签到者",
        visitStatus: "NOT_CHECKED_IN",
        checkedInAt: null,
        canStartVisit: false,
        canCompleteVisit: false,
      },
      {
        appointmentId: "2",
        visitNumber: 2,
        patientName: "候诊者",
        visitStatus: "WAITING",
        checkedInAt: "2026-11-14T07:40:00",
        canStartVisit: true,
        canCompleteVisit: false,
      },
      {
        appointmentId: "3",
        visitNumber: 3,
        patientName: "接诊者",
        visitStatus: "IN_PROGRESS",
        checkedInAt: "2026-11-14T07:42:00",
        canStartVisit: false,
        canCompleteVisit: true,
      },
      {
        appointmentId: "4",
        visitNumber: 4,
        patientName: "已就诊者",
        visitStatus: "COMPLETED",
        checkedInAt: "2026-11-14T07:43:00",
        canStartVisit: false,
        canCompleteVisit: false,
      },
    ] as DoctorRow[],
    failHospitals: false,
    unauthorized: false,
    unknownCreate: false,
    confirmationReads: 0,
    bindings: [] as unknown[],
  };
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname.slice("/api/v1".length),
      method = request.method();
    const body = request.postDataJSON() || {};
    const ok = (data: unknown, status = 200) =>
      route.fulfill({ status, json: { code: 0, message: "success", data } });
    const fail = (status: number, code: number, message: string) =>
      route.fulfill({ status, json: { code, message, data: null } });
    if (state.unauthorized && !path.startsWith("/auth/"))
      return fail(401, 2001, "登录已过期");
    if (path === "/auth/login") return ok({ ...login, user });
    if (path === "/auth/register") return ok(user, 201);
    if (path === "/users/me") return ok(user);
    if (path === "/hospitals")
      return state.failHospitals
        ? fail(503, 1002, "目录暂时不可用")
        : ok(state.hospitals);
    if (/^\/hospitals\/[^/]+\/departments$/.test(path))
      return ok(state.departments);
    if (/^\/departments\/[^/]+\/doctors$/.test(path)) return ok(state.doctors);
    if (/^\/doctors\/[^/]+\/schedules$/.test(path)) {
      const types = [
        "MORNING_FIRST",
        "MORNING_SECOND",
        "AFTERNOON_FIRST",
        "AFTERNOON_SECOND",
      ] as const;
      const starts = ["08:00:00", "10:30:00", "14:00:00", "16:30:00"],
        ends = ["10:00:00", "12:00:00", "16:00:00", "18:00:00"];
      return ok(
        ["2026-11-14", "2026-11-15", "2026-11-16"].flatMap((date) =>
          types.map((type, i) =>
            schedule({
              appointmentDate: date,
              sessionType: type,
              startTime: starts[i],
              endTime: ends[i],
              bookingClosesAt:
                date +
                "T" +
                ["07:45:00", "10:15:00", "13:45:00", "16:15:00"][i],
            }),
          ),
        ),
      );
    }
    if (path === "/patients" && method === "GET") return ok(state.patients);
    if (path === "/patients" && method === "POST") {
      state.patientWrites.push(body);
      const p = {
        ...patient,
        ...body,
        id: String(201 + state.patients.length),
      };
      state.patients.push(p);
      return ok(p, 201);
    }
    if (/^\/patients\/[^/]+$/.test(path) && method === "PATCH") {
      state.patientWrites.push(body);
      const p = state.patients.find((p) => p.id === path.split("/")[2])!;
      Object.assign(p, body);
      return ok(p);
    }
    if (path.endsWith("/status")) {
      const p = state.patients.find((p) => p.id === path.split("/")[2])!;
      p.enabled = body.enabled;
      return ok(p);
    }
    if (path === "/appointments" && method === "POST") {
      ++state.creates;
      state.keys.push(request.headers()["idempotency-key"]);
      if (state.unknownCreate && state.creates === 1)
        return route.abort("failed");
      state.appointment = appointment({
        appointmentDate: body.appointmentDate,
        sessionType: body.sessionType,
        patient: state.patients.find((p) => p.id === body.patientId)!,
      });
      return ok(
        state.creates > 1
          ? {
              appointmentOrderId: "301",
              appointmentNo: "TEST-AP301",
              replayed: true,
            }
          : {
              appointmentOrderId: "301",
              paymentOrderId: "501",
              amount: "10.00",
              paymentExpiresAt: "2026-11-14T09:55:00",
              serverTime: "2026-11-14T09:40:00",
            },
        state.creates > 1 ? 200 : 201,
      );
    }
    if (path === "/appointments/my") return ok([state.appointment]);
    if (/^\/appointments\/[^/]+$/.test(path)) {
      if (
        state.appointment.workflowStatus === "CONFIRMING_APPOINTMENT" &&
        ++state.confirmationReads > 1
      ) {
        Object.assign(state.appointment, {
          status: "CONFIRMED",
          workflowStatus: "CONFIRMED",
          canPay: false,
          visitNumber: 3,
          visit: {
            status: "NOT_CHECKED_IN",
            canCheckIn: false,
            checkInOpensAt: "2026-11-16T13:30:00",
            checkInClosesAt: "2026-11-16T16:00:00",
            checkedInAt: null,
            startedAt: null,
            completedAt: null,
          },
        });
      }
      return ok(state.appointment);
    }
    if (path.endsWith("/cancel")) {
      Object.assign(state.appointment, {
        status: "CANCELLED",
        paymentStatus: "REFUND_PENDING",
        workflowStatus: "CANCELLED_REFUNDING",
        canCancel: false,
      });
      return ok(state.appointment, 202);
    }
    if (path.endsWith("/check-in")) {
      state.appointment.visit!.status = "WAITING";
      state.appointment.visit!.canCheckIn = false;
      return ok(state.appointment);
    }
    if (path.startsWith("/payments/")) {
      Object.assign(state.appointment, {
        paymentStatus: "PAYING",
        workflowStatus: "PAYMENT_PROCESSING",
        canPay: false,
        latestPaymentAttempt: {
          id: "601",
          attemptNo: "attempt-601",
          channel: "MOCK",
          status: "PROCESSING",
          providerOrderNo: "provider",
          failureCode: null,
          createdAt: "2026-11-14T09:40:00",
          completedAt: null,
        },
      });
      return ok(
        {
          paymentOrderId: "501",
          paymentAttemptId: "601",
          channel: "MOCK",
          paymentStatus: "PAYING",
          attemptStatus: "PROCESSING",
          providerOrderNo: "provider",
          checkoutUrl: "/api/v1/mock-pay/attempts/attempt-601",
          expiresAt: state.appointment.paymentExpiresAt,
        },
        202,
      );
    }
    if (path.startsWith("/mock-pay/")) {
      if (path.endsWith("/complete"))
        Object.assign(state.appointment, {
          paymentStatus: "PAID",
          workflowStatus: "CONFIRMING_APPOINTMENT",
          canCancel: false,
        });
      else
        Object.assign(state.appointment, {
          paymentStatus: "UNPAID",
          workflowStatus: "WAITING_FOR_PAYMENT",
          canPay: true,
          latestPaymentAttempt: {
            ...state.appointment.latestPaymentAttempt,
            status: "FAILED",
          },
        });
      return ok({}, 202);
    }
    if (path === "/doctor/sessions") {
      const types = [
        "MORNING_FIRST",
        "MORNING_SECOND",
        "AFTERNOON_FIRST",
        "AFTERNOON_SECOND",
      ] as const;
      return ok(
        types.map((type, i) => ({
          appointmentDate: url.searchParams.get("date"),
          sessionType: type,
          startTime: ["08:00:00", "10:30:00", "14:00:00", "16:30:00"][i],
          endTime: ["10:00:00", "12:00:00", "16:00:00", "18:00:00"][i],
          confirmedCount: i ? 0 : state.rows.length,
          notCheckedInCount: i
            ? 0
            : state.rows.filter((r) => r.visitStatus === "NOT_CHECKED_IN")
                .length,
          waitingCount: i
            ? 0
            : state.rows.filter((r) => r.visitStatus === "WAITING").length,
          inProgressCount: i
            ? 0
            : state.rows.filter((r) => r.visitStatus === "IN_PROGRESS").length,
          completedCount: i
            ? 0
            : state.rows.filter((r) => r.visitStatus === "COMPLETED").length,
        })),
      );
    }
    if (path === "/doctor/appointments")
      return ok(
        url.searchParams.get("sessionType") === "MORNING_FIRST"
          ? state.rows
          : [],
      );
    if (path.startsWith("/doctor/appointments/")) {
      const row = state.rows.find(
        (r) => r.appointmentId === path.split("/")[3],
      )!;
      if (path.endsWith("/start-visit"))
        Object.assign(row, {
          visitStatus: "IN_PROGRESS",
          canStartVisit: false,
          canCompleteVisit: true,
        });
      if (path.endsWith("/complete-visit"))
        Object.assign(row, {
          visitStatus: "COMPLETED",
          canStartVisit: false,
          canCompleteVisit: false,
        });
      return ok({
        appointmentId: row.appointmentId,
        appointmentNo: "AP" + row.appointmentId,
        visitNumber: row.visitNumber,
        appointmentDate: "2026-11-14",
        startTime: "08:00:00",
        endTime: "10:00:00",
        patient: { ...patient, name: row.patientName },
        doctor: { ...doctor, department, hospital },
        visit: {
          status: row.visitStatus,
          checkedInAt: row.checkedInAt,
          startedAt: null,
          completedAt: null,
        },
      });
    }
    if (path === "/admin/booking-settings") {
      if (method === "PUT") Object.assign(state.settings, body);
      return ok(state.settings);
    }
    if (path.endsWith("/account")) {
      const value = { doctorId: path.split("/")[3], userId: body.userId };
      state.bindings.push(value);
      return ok(value);
    }
    if (path.startsWith("/admin/") && method === "DELETE")
      return fail(409, 4402, "该资源仍被引用，无法删除");
    if (path.startsWith("/admin/") && (method === "POST" || method === "PATCH"))
      return ok({ id: "new-id", ...body });
    return fail(404, 4001, "测试接口未定义：" + path);
  });
  return state;
}
