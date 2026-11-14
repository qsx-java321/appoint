<script setup lang="ts">
import { ref, watch } from "vue";
import { api } from "../api";
import type { Appointment } from "../api/types";
import { useRequest } from "../composables/useRequest";
import RequestState from "../components/RequestState.vue";
import { label, money } from "../lib/presentation";
const order = ref("DESC");
const { data, loading, error, run } = useRequest<Appointment[]>([]);
const load = () => {
  const sorting = order.value;
  return run(() => api.appointments(sorting));
};
watch(order, load, { immediate: true });
</script>
<template>
  <div class="section-heading">
    <div>
      <h1>我的预约</h1>
      <p class="lead">当前账号的全部预约记录</p>
    </div>
    <label
      >就诊时间<select v-model="order">
        <option value="DESC">较晚日期在前（默认）</option>
        <option value="ASC">较早日期在前</option>
      </select></label
    >
  </div>
  <RequestState
    :loading="loading"
    :error="error"
    :empty="!data.length"
    empty-text="暂无预约记录"
    @retry="load"
    ><div class="stack">
      <article v-for="a in data" :key="a.id" class="panel stack">
        <div class="section-heading">
          <div>
            <span class="tag">{{ label(a) }}</span>
            <h2>
              {{ a.appointmentDate }} · {{ a.startTime.slice(0, 5) }}–{{
                a.endTime.slice(0, 5)
              }}
            </h2>
            <p>
              {{ a.doctor.hospital.name }} · {{ a.doctor.department.name }} ·
              {{ a.doctor.name }}
            </p>
          </div>
          <RouterLink class="button primary" :to="`/appointments/${a.id}`"
            >查看详情</RouterLink
          >
        </div>
        <div class="actions muted">
          <span>就诊人 {{ a.patient.name }}</span
          ><span>挂号费 ¥{{ money(a.amount) }}</span
          ><span
            >就诊序号
            {{
              a.visitNumber == null
                ? "—"
                : String(a.visitNumber).padStart(3, "0")
            }}</span
          >
        </div>
      </article>
    </div></RequestState
  >
</template>
