<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import { message } from "../api/client";
import type { Appointment } from "../api/types";
import { useRequest } from "../composables/useRequest";
import RequestState from "../components/RequestState.vue";
import {
  epoch,
  label,
  money,
  sessionNames,
  time,
  visitNames,
  withinPaymentDeadline,
} from "../lib/presentation";
const paymentNames: Record<string, string> = {
  UNPAID: "未支付",
  PAYING: "未支付",
  PAID: "已支付",
  REFUND_PENDING: "历史订单已关闭",
  REFUNDED: "历史订单已关闭",
  CLOSED: "已关闭",
};
const route = useRoute();
const { data, loading, error, run, invalidate } =
  useRequest<Appointment | null>(null);
const busy = ref(false),
  actionError = ref(""),
  notice = ref(""),
  fetchedAt = ref(Date.now()),
  now = ref(Date.now());
const paymentPage = computed(() => !!route.meta.payment);
const serverNow = computed(() =>
  data.value ? epoch(data.value.serverTime) + now.value - fetchedAt.value : 0,
);
const canPay = computed(
  () =>
    !!data.value?.canPay &&
    withinPaymentDeadline(serverNow.value, data.value.paymentExpiresAt),
);
const canCancel = computed(
  () =>
    !!data.value?.canCancel &&
    withinPaymentDeadline(serverNow.value, data.value.paymentExpiresAt),
);
const currentLabel = computed(() => {
  const appointment = data.value;
  if (!appointment) return "";
  if (appointment.canPay && !canPay.value) return "预约已过期";
  return label(appointment);
});
const canCheckIn = computed(
  () =>
    !!data.value?.visit?.canCheckIn &&
    serverNow.value < epoch(data.value.visit.checkInClosesAt),
);
async function load() {
  if (loading.value) return false;
  const id = String(route.params.id);
  await run(() => api.appointment(id));
  if (data.value?.id === id && String(route.params.id) === id) {
    fetchedAt.value = Date.now();
    now.value = Date.now();
  }
  return !error.value;
}
async function act(action: "cancel" | "checkIn" | "pay") {
  if (busy.value || loading.value || !data.value) return;
  const a = data.value,
    id = a.id;
  if (
    action === "cancel" &&
    !window.confirm(`取消${a.patient.name}在${a.appointmentDate}的预约？`)
  )
    return;
  if (
    action === "checkIn" &&
    !window.confirm("请确认已经到院，签到后进入候诊。")
  )
    return;
  busy.value = true;
  actionError.value = "";
  notice.value = "";
  try {
    const updated = await api[action](id);
    if (String(route.params.id) === id) {
      data.value = updated;
      fetchedAt.value = Date.now();
      now.value = fetchedAt.value;
      notice.value = action === "pay" ? "支付成功，预约已确认。" : "操作成功。";
    }
  } catch (e) {
    if (String(route.params.id) === id) {
      actionError.value = message(e);
      await load();
    }
  } finally {
    busy.value = false;
  }
}
watch(
  () => route.params.id,
  () => {
    invalidate();
    data.value = null;
    actionError.value = "";
    notice.value = "";
    void load();
  },
  { immediate: true },
);
let clock: ReturnType<typeof setInterval>;
onMounted(() => {
  clock = setInterval(() => {
    now.value = Date.now();
  }, 1000);
});
onUnmounted(() => {
  clearInterval(clock);
  invalidate();
});
</script>
<template>
  <RouterLink to="/appointments">我的预约</RouterLink>
  <h1>{{ paymentPage ? "支付与预约结果" : "预约详情" }}</h1>
  <p v-if="actionError" class="notice error" role="alert">{{ actionError }}</p>
  <p v-if="notice" class="notice" role="status">{{ notice }}</p>
  <RequestState :loading="loading && !data" :error="error" @retry="load"
    ><template v-if="data"
      ><section class="status-panel">
        <div>
          <span class="eyebrow">当前预约状态</span>
          <h2>{{ currentLabel }}</h2>
          <p v-if="canPay">最晚支付时间 {{ time(data.paymentExpiresAt) }}。</p>
          <p v-else-if="data.visit?.status === 'NOT_CHECKED_IN'">
            {{
              serverNow >= epoch(data.visit.checkInClosesAt)
                ? "签到时间已结束，预约仍为已确认，尚未签到。"
                : "请按预约日期到院，并在签到窗口内操作。"
            }}
          </p>
          <p v-else-if="data.visit?.status === 'WAITING'">
            请留意现场安排，就诊序号不代表预计等候时间。
          </p>
        </div>
        <div class="actions">
          <RouterLink
            v-if="canPay && !paymentPage"
            class="button primary"
            :to="`/appointments/${data.id}/payment`"
            >去支付</RouterLink
          ><button
            v-if="data.visit"
            class="primary"
            :disabled="!canCheckIn || busy"
            @click="act('checkIn')"
          >
            {{
              data.visit.status === "NOT_CHECKED_IN" ? "到院签到" : "已签到"
            }}</button
          ><button v-if="canCancel" :disabled="busy" @click="act('cancel')">
            取消预约</button
          ><button :disabled="loading || busy" @click="load">
            {{ loading ? "正在刷新…" : "刷新进度" }}
          </button>
        </div>
      </section>
      <section v-if="paymentPage && canPay" class="panel stack">
        <h2>模拟支付</h2>
        <p>本次为模拟支付。</p>
        <button
          type="button"
          class="primary align-start"
          :disabled="busy"
          @click="act('pay')"
        >
          {{ busy ? "正在确认…" : `模拟支付 ¥${money(data.amount)}` }}
        </button>
      </section>
      <div class="detail-grid">
        <section class="panel stack">
          <h2>就诊凭证</h2>
          <p class="ticket-date">{{ data.appointmentDate }}</p>
          <p>
            {{ data.startTime.slice(0, 5) }}–{{ data.endTime.slice(0, 5) }} ·
            {{ sessionNames[data.sessionType || ""] || "历史场次" }}
          </p>
          <p>
            {{ data.doctor.hospital.name }} · {{ data.doctor.department.name }}
          </p>
          <p>{{ data.doctor.name }} · {{ data.doctor.title }}</p>
          <p class="muted">{{ data.doctor.hospital.address }}</p>
          <dl class="facts">
            <dt>就诊人</dt>
            <dd>{{ data.patient.name }}</dd>
            <dt>身份证</dt>
            <dd>{{ data.patient.documentNoMasked }}</dd>
            <dt>就诊序号</dt>
            <dd>
              {{
                data.visitNumber == null
                  ? "—"
                  : String(data.visitNumber).padStart(3, "0")
              }}
            </dd>
            <dt>预约单号</dt>
            <dd>{{ data.appointmentNo }}</dd>
          </dl>
          <p v-if="data.visit" class="notice">
            首次签到：{{ time(data.visit.checkInOpensAt) }} 至
            {{ time(data.visit.checkInClosesAt) }}，结束时刻截止。
          </p>
        </section>
        <section class="panel">
          <h2>支付信息</h2>
          <dl class="facts">
            <dt>挂号费</dt>
            <dd>¥{{ money(data.amount) }} {{ data.currency }}</dd>
            <dt>支付状态</dt>
            <dd>
              {{ paymentNames[data.paymentStatus] || data.paymentStatus }}
            </dd>
            <dt>最晚支付时间</dt>
            <dd>{{ time(data.paymentExpiresAt) }}</dd>
            <template v-if="data.paidAt">
              <dt>支付时间</dt>
              <dd>{{ time(data.paidAt) }}</dd>
            </template>
          </dl>
        </section>
      </div>
      <section v-if="data.visit" class="panel">
        <h2>就诊进度 · {{ visitNames[data.visit.status] }}</h2>
        <dl class="facts">
          <dt>签到时间</dt>
          <dd>{{ time(data.visit.checkedInAt) }}</dd>
          <dt>开始接诊</dt>
          <dd>{{ time(data.visit.startedAt) }}</dd>
          <dt>完成接诊</dt>
          <dd>{{ time(data.visit.completedAt) }}</dd>
        </dl>
      </section></template
    ></RequestState
  >
</template>
