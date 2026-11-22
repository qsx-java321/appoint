import { defineStore } from "pinia";
import { ref } from "vue";
import type {
  BookingInput,
  Doctor,
  Department,
  Hospital,
  Schedule,
} from "../api/types";
import { api } from "../api";
import { ApiError } from "../api/client";
import { readSession } from "../lib/session";
import { environmentStorageKey } from "../lib/environment";
export interface Selection {
  hospital: Hospital;
  department: Department;
  doctor: Doctor;
  schedule: Schedule;
}
export interface Submission {
  owner: string;
  key: string;
  body: BookingInput;
}
const key = environmentStorageKey("submission");
export function readSubmission(): Submission | null {
  try {
    const p = JSON.parse(
      sessionStorage.getItem(key) || "null",
    ) as Submission | null;
    if (
      p &&
      p.owner === readSession()?.user.id &&
      typeof p.key === "string" &&
      typeof p.body?.patientId === "string"
    )
      return p;
  } catch {
    /* 旧草稿不可用时重新选择。 */
  }
  return null;
}
export const useBooking = defineStore("booking", () => {
  const selection = ref<Selection | null>(null);
  const pending = ref<Submission | null>(readSubmission());
  const busy = ref(false);
  let issued: { owner: string; token: string; expiresAt: number } | null = null;
  function clear() {
    selection.value = null;
    pending.value = null;
    issued = null;
    sessionStorage.removeItem(key);
  }
  window.addEventListener("appoint:logout", clear);

  async function prepare() {
    const owner = readSession()?.user.id;
    if (!owner) throw new Error("请先登录或重新登录。");
    const now = Date.now();
    if (issued?.owner === owner && issued.expiresAt > now) return issued.token;
    const response = await api.bookingToken();
    if (readSession()?.user.id !== owner) throw new Error("登录账号已切换。");
    issued = {
      owner,
      token: response.token,
      expiresAt: now + response.expiresIn * 1000,
    };
    return issued.token;
  }

  async function create(patientId?: string) {
    if (busy.value) throw new Error("预约正在提交，请稍候。");
    busy.value = true;
    try {
      if (!pending.value) {
        const s = selection.value,
          owner = readSession()?.user.id;
        if (!s || !patientId || !owner)
          throw new Error("请重新登录并选择预约信息。");
        const token = await prepare();
        pending.value = {
          owner,
          key: token,
          body: {
            doctorId: s.doctor.id,
            appointmentDate: s.schedule.appointmentDate,
            sessionType: s.schedule.sessionType,
            patientId,
          },
        };
        issued = null;
        sessionStorage.setItem(key, JSON.stringify(pending.value));
      }
      const { owner, body, key: token } = pending.value;
      const result = await api.createAppointment(body, token);
      if (readSession()?.user.id !== owner) throw new Error("登录账号已切换。");
      if ("pending" in result) return null;
      clear();
      return result.appointmentOrderId;
    } catch (error) {
      if (error instanceof ApiError && !error.uncertain) {
        pending.value = null;
        sessionStorage.removeItem(key);
      }
      throw error;
    } finally {
      busy.value = false;
    }
  }
  return { selection, pending, busy, prepare, create, clear };
});
