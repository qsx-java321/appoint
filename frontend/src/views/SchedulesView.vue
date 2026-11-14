<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import type { Schedule, Hospital, Department, Doctor } from "../api/types";
import { useBooking } from "../stores/booking";
import { useRequest } from "../composables/useRequest";
import RequestState from "../components/RequestState.vue";
import { availabilityNames, money, time } from "../lib/presentation";
const route = useRoute(),
  router = useRouter(),
  booking = useBooking();
const selected = ref<Schedule | null>(null),
  day = ref("");
const { data, loading, error, run } = useRequest<{
  schedules: Schedule[];
  hospital?: Hospital;
  department?: Department;
  doctor?: Doctor;
}>({ schedules: [] });
const dates = computed(() => [
  ...new Set(data.value.schedules.map((s) => s.appointmentDate)),
]);
async function load() {
  selected.value = null;
  const doctorId = String(route.params.doctorId),
    hospitalId = String(route.query.hospitalId || ""),
    departmentId = String(route.query.departmentId || "");
  await run(async () => {
    const [hospitals, departments, doctors, schedules] = await Promise.all([
      api.hospitals(),
      hospitalId ? api.departments(hospitalId) : Promise.resolve([]),
      departmentId ? api.doctors(departmentId) : Promise.resolve([]),
      api.schedules(doctorId),
    ]);
    return {
      schedules,
      hospital: hospitals.find((h) => h.id === hospitalId),
      department: departments.find((d) => d.id === departmentId),
      doctor: doctors.find((d) => d.id === doctorId),
    };
  });
  day.value = dates.value.includes(day.value)
    ? day.value
    : dates.value[0] || "";
}
function next() {
  const { hospital, department, doctor } = data.value;
  if (!hospital || !department || !doctor || !selected.value) return;
  booking.selection = {
    hospital,
    department,
    doctor,
    schedule: selected.value,
  };
  void router.push("/booking/confirm");
}
watch(() => route.fullPath, load, { immediate: true });
</script>
<template>
  <RouterLink
    :to="
      route.query.hospitalId
        ? `/booking/hospitals/${route.query.hospitalId}`
        : '/booking'
    "
    >返回科室医生</RouterLink
  ><RequestState :loading="loading" :error="error" @retry="load"
    ><section class="panel doctor-card">
      <span class="avatar" aria-hidden="true">{{
        data.doctor?.name[0] || "医"
      }}</span>
      <div>
        <h1>{{ data.doctor?.name || "医生场次" }}</h1>
        <p>
          {{ data.hospital?.name }} · {{ data.department?.name }} ·
          {{ data.doctor?.title }}
        </p>
      </div>
    </section>
    <p v-if="!data.doctor || !data.hospital || !data.department" class="notice">
      请从医院目录重新选择医生，以核对完整预约信息。
    </p>
    <div class="booking-layout">
      <section class="panel">
        <div class="section-heading">
          <h2>选择就诊场次</h2>
          <button @click="load">刷新场次</button>
        </div>
        <div class="mobile-dates">
          <button
            v-for="date in dates"
            :key="date"
            :aria-pressed="day === date"
            @click="day = date"
          >
            {{ date }}
          </button>
        </div>
        <div class="days">
          <section
            v-for="date in dates"
            :key="date"
            class="day"
            :class="{ 'mobile-hidden': date !== day }"
          >
            <h3>{{ date }}</h3>
            <button
              v-for="s in data.schedules.filter(
                (s) => s.appointmentDate === date,
              )"
              :key="s.sessionType"
              class="slot"
              :disabled="!s.bookable"
              :aria-pressed="selected === s"
              @click="selected = s"
            >
              <strong
                >{{ s.startTime.slice(0, 5) }}–{{
                  s.endTime.slice(0, 5)
                }}</strong
              ><span
                >{{
                  s.bookable
                    ? `余 ${s.availableCapacity} 号`
                    : availabilityNames[s.availability]
                }}
                · ¥{{ money(s.registrationFee) }}</span
              ><small>停止新下单 {{ s.bookingClosesAt.slice(11, 16) }}</small>
            </button>
          </section>
        </div>
        <p v-if="!dates.length" class="feedback">
          暂无可查询场次，请重新选择医生。
        </p>
        <p class="muted">
          各场开诊前15分钟停止新预约；可用名额以提交结果为准。
        </p>
      </section>
      <aside class="panel stack">
        <h2>本次预约</h2>
        <p>{{ selected?.appointmentDate || "请选择场次" }}</p>
        <p v-if="selected">
          {{ selected.startTime.slice(0, 5) }}–{{
            selected.endTime.slice(0, 5)
          }}
        </p>
        <p class="price">
          {{ selected ? `¥${money(selected.registrationFee)}` : "—" }}
        </p>
        <p v-if="selected" class="muted">
          停止新下单：{{ time(selected.bookingClosesAt) }}
        </p>
        <p>选中场次后尚未占号，下一步核对就诊人。</p>
        <button
          class="primary"
          :disabled="
            !selected || !data.doctor || !data.hospital || !data.department
          "
          @click="next"
        >
          下一步：核对预约
        </button>
      </aside>
    </div></RequestState
  >
</template>
