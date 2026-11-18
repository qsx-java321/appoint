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
  function clear() {
    selection.value = null;
    pending.value = null;
    sessionStorage.removeItem(key);
  }
  window.addEventListener("appoint:logout", clear);
  async function create(patientId?: string) {
    if (busy.value) throw new Error("预约正在提交，请稍候。");
    if (!pending.value) {
      const s = selection.value,
        owner = readSession()?.user.id;
      if (!s || !patientId || !owner)
        throw new Error("请重新登录并选择预约信息。");
      pending.value = {
        owner,
        key: crypto.randomUUID(),
        body: {
          doctorId: s.doctor.id,
          appointmentDate: s.schedule.appointmentDate,
          sessionType: s.schedule.sessionType,
          patientId,
        },
      };
      sessionStorage.setItem(key, JSON.stringify(pending.value));
    }
    busy.value = true;
    try {
      const result = await api.createAppointment(
        pending.value.body,
        pending.value.key,
      );
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
  return { selection, pending, busy, create, clear };
});
