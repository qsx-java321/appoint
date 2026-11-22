<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import type { Patient } from "../api/types";
import { message } from "../api/client";
import { useBooking } from "../stores/booking";
import { useRequest } from "../composables/useRequest";
import { money, time, sessionNames } from "../lib/presentation";
import RequestState from "../components/RequestState.vue";
const route = useRoute(),
  router = useRouter(),
  booking = useBooking();
const selected = computed(() => booking.selection);
const { data, loading, error, run } = useRequest<Patient[]>([]);
const patientId = ref(String(route.query.patientId || "")),
  acknowledged = ref(false),
  submitError = ref(""),
  valid = ref(false);
const submitStatus = computed(() =>
  booking.pending && !booking.busy
    ? "预约结果尚未确认。再次点击「提交预约」会继续查询原预约。"
    : "",
);
async function load() {
  valid.value = false;
  await run(async () => {
    const patients = await api.patients(),
      s = selected.value;
    if (s) {
      const schedules = await api.schedules(s.doctor.id);
      const latest = schedules.find(
        (v) =>
          v.appointmentDate === s.schedule.appointmentDate &&
          v.sessionType === s.schedule.sessionType,
      );
      if (latest) {
        booking.selection = { ...s, schedule: latest };
        valid.value = latest.bookable;
        if (latest.bookable && patients.some((p) => p.enabled))
          await booking.prepare();
      }
    }
    return patients.filter((p) => p.enabled);
  });
  if (!data.value.some((p) => p.id === patientId.value))
    patientId.value = data.value[0]?.id || "";
}
async function submit() {
  if (booking.busy) return;
  submitError.value = "";
  try {
    const id = await booking.create(patientId.value);
    if (id) await router.replace(`/appointments/${id}/payment`);
  } catch (e) {
    submitError.value = message(e);
    if (!booking.pending) await load();
  }
}
onMounted(() => {
  if (!booking.pending) void load();
});
</script>
<template>
  <h1>核对这次预约</h1>
  <p class="lead">提交前核对就诊人、时间、费用与取消限制。</p>
  <p v-if="submitError" class="notice error" role="alert">{{ submitError }}</p>
  <div role="status">
    <p v-if="submitStatus" class="notice">{{ submitStatus }}</p>
  </div>
  <section v-if="!selected && !booking.pending" class="panel feedback">
    <p>尚未选择场次，或页面刷新后选择已失效。</p>
    <RouterLink class="button primary" to="/booking">重新选择预约</RouterLink>
  </section>
  <RequestState
    v-else
    :loading="!booking.pending && loading"
    :error="booking.pending ? undefined : error"
    @retry="load"
    ><div class="booking-layout">
      <section v-if="booking.pending" class="panel stack">
        <h2>本次预约</h2>
        <p>
          {{ booking.pending.body.appointmentDate }} ·
          {{ sessionNames[booking.pending.body.sessionType] }}
        </p>
      </section>
      <section v-else-if="selected" class="stack">
        <section class="panel">
          <div class="section-heading">
            <h2>为谁预约</h2>
            <RouterLink
              :to="{
                path: '/patients',
                query: { new: '1', returnTo: 'confirm' },
              }"
              >新增就诊人</RouterLink
            >
          </div>
          <div class="card-grid">
            <label v-for="p in data" :key="p.id" class="patient-choice"
              ><input v-model="patientId" type="radio" :value="p.id" /><span
                >{{ p.name
                }}<small>身份证 {{ p.documentNoMasked }}</small></span
              ></label
            >
          </div>
          <p v-if="!data.length">请先新增或启用就诊人。</p>
        </section>
        <section class="panel">
          <h2>就诊安排</h2>
          <dl class="facts">
            <dt>医院</dt>
            <dd>{{ selected.hospital.name }}</dd>
            <dt>科室与医生</dt>
            <dd>
              {{ selected.department.name }} · {{ selected.doctor.name }} ·
              {{ selected.doctor.title }}
            </dd>
            <dt>日期与场次</dt>
            <dd>
              {{ selected.schedule.appointmentDate }}
              {{ selected.schedule.startTime.slice(0, 5) }}–{{
                selected.schedule.endTime.slice(0, 5)
              }}
            </dd>
            <dt>停止新下单</dt>
            <dd>{{ time(selected.schedule.bookingClosesAt) }}</dd>
          </dl>
          <p class="muted">
            到院首次签到：开诊前30分钟起至场次结束前。订单支付截止在提交成功后显示。
          </p>
        </section>
      </section>
      <aside class="panel stack">
        <template v-if="selected && !booking.pending">
          <h2>费用与规则</h2>
          <p class="price">¥{{ money(selected.schedule.registrationFee) }}</p>
          <p id="booking-cancel-policy">
            支付期限内可以取消待支付预约。付款确认后无法自助取消。
          </p>
          <p>提交前尚未占号，最终名额与费用以创建结果为准。</p>
          <p v-if="!valid" class="notice error">
            当前场次不可新预约，请返回重新选择。
          </p>
          <label class="check-line"
            ><input
              v-model="acknowledged"
              type="checkbox"
              aria-describedby="booking-cancel-policy"
            /><span>我已核对预约信息，并知晓取消规则。</span></label
          >
        </template>
        <button
          class="primary"
          :disabled="
            booking.busy ||
            (!booking.pending && (!valid || !patientId || !acknowledged))
          "
          @click="submit"
        >
          {{ booking.busy ? "正在提交…" : "提交预约" }}</button><RouterLink
          v-if="selected && !booking.pending"
          :to="{
            path: `/booking/doctors/${selected.doctor.id}`,
            query: {
              hospitalId: selected.hospital.id,
              departmentId: selected.department.id,
            },
          }"
          >返回选择场次</RouterLink
        >
      </aside>
    </div></RequestState
  >
</template>
