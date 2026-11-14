import { computed, ref, watch } from "vue";
import { api } from "../api";
import type { Department, Doctor, Hospital } from "../api/types";
import { useRequest } from "./useRequest";

export function useCatalog() {
  const hospitalId = ref(""),
    departmentId = ref("");
  const h = useRequest<Hospital[]>([]),
    d = useRequest<Department[]>([]),
    p = useRequest<Doctor[]>([]);
  async function load() {
    await h.run(api.hospitals);
    if (!h.data.value.some((v) => v.id === hospitalId.value))
      hospitalId.value = h.data.value[0]?.id || "";
  }
  async function loadDepartments() {
    const parent = hospitalId.value;
    d.invalidate();
    p.invalidate();
    d.data.value = [];
    p.data.value = [];
    departmentId.value = "";
    if (!parent) return;
    await d.run(() => api.departments(parent));
    if (hospitalId.value === parent)
      departmentId.value = d.data.value[0]?.id || "";
  }
  async function loadDoctors() {
    const parent = departmentId.value;
    p.invalidate();
    p.data.value = [];
    if (parent) await p.run(() => api.doctors(parent));
  }
  async function refresh() {
    await load();
    const parent = hospitalId.value;
    if (parent) {
      await d.run(() => api.departments(parent));
      if (!d.data.value.some((v) => v.id === departmentId.value))
        departmentId.value = d.data.value[0]?.id || "";
      await loadDoctors();
    }
  }
  watch(hospitalId, loadDepartments);
  watch(departmentId, loadDoctors);
  return {
    hospitalId,
    departmentId,
    hospitals: h.data,
    departments: d.data,
    doctors: p.data,
    loading: computed(
      () => h.loading.value || d.loading.value || p.loading.value,
    ),
    error: computed(() => h.error.value || d.error.value || p.error.value),
    load,
    refresh,
  };
}
