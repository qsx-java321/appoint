<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import { message } from "../api/client";
import type { Appointment } from "../api/types";
import { useRequest } from "../composables/useRequest";
import { usePolling } from "../composables/usePolling";
import RequestState from "../components/RequestState.vue";
import {
  epoch,
  label,
  money,
  needsPolling,
  refundReasons,
  sessionNames,
  time,
  visitNames,
} from "../lib/presentation";
const paymentNames: Record<string, string> = {
  UNPAID: "未支付",
  PAYING: "支付处理中",
  PAID: "已支付",
  REFUND_PENDING: "退款处理中",
  REFUNDED: "已退款",
  CLOSED: "已关闭",
};
const route = useRoute();
const { data, loading, error, run, invalidate } =
  useRequest<Appointment | null>(null);
const busy = ref(false),
  actionError = ref(""),
  notice = ref(""),
  checkout = ref(""),
  fetchedAt = ref(Date.now()),
  now = ref(Date.now());
const mockEnabled = import.meta.env.VITE_ENABLE_MOCK_PAY === "true";
const paymentPage = computed(() => !!route.meta.payment);
const serverNow = computed(() =>
  data.value ? epoch(data.value.serverTime) + now.value - fetchedAt.value : 0,
);
const canPay = computed(
  () =>
    !!data.value?.canPay &&
    serverNow.value < epoch(data.value.paymentExpiresAt),
);
const canCancel = computed(
  () =>
    !!data.value?.canCancel &&
    (data.value.status !== "CONFIRMED" ||
      serverNow.value <
        epoch(`${data.value.appointmentDate}T${data.value.startTime}`) -
          24 * 3600000),
);
const canCheckIn = computed(
  () =>
    !!data.value?.visit?.canCheckIn &&
    serverNow.value < epoch(data.value.visit.checkInClosesAt),
);
const pollingEnabled = computed(
  () => !!data.value && needsPolling(data.value) && !error.value,
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
const { exhausted, restart } = usePolling(pollingEnabled, load);
async function manual() {
  await load();
  restart();
}
async function act(action: "cancel" | "checkIn" | "pay" | "complete" | "fail") {
  if (busy.value || !data.value) return;
  const a = data.value,
    id = a.id;
  if (
    action === "cancel" &&
    !window.confirm(
      `取消${a.patient.name}在${a.appointmentDate}的预约？${a.paymentStatus === "PAID" ? "受理后进入原路全额退款，请继续查看结果。" : "以服务端最新支付与预约状态为准。"}`,
    )
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
    if (action === "cancel") await api.cancel(id);
    else if (action === "checkIn") await api.checkIn(id);
    else if (action === "pay") {
      const result = await api.pay(a.paymentOrderId);
      if (String(route.params.id) === id) checkout.value = result.checkoutUrl;
    } else {
      const path =
        checkout.value ||
        (a.latestPaymentAttempt?.channel === "MOCK"
          ? `/api/v1/mock-pay/attempts/${encodeURIComponent(a.latestPaymentAttempt.attemptNo)}`
          : "");
      await api.mock(path, action);
    }
    if (String(route.params.id) === id) {
      notice.value =
        action === "complete" || action === "fail"
          ? "模拟通知已受理，请查询正式处理结果。"
          : "操作已受理，当前状态以最新查询为准。";
      await load();
      restart();
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
const activeMock = computed(
  () =>
    data.value?.latestPaymentAttempt?.channel === "MOCK" &&
    data.value.latestPaymentAttempt.status === "PROCESSING",
);
watch(
  () => route.params.id,
  () => {
    invalidate();
    data.value = null;
    checkout.value = "";
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
  <RequestState :loading="loading && !data" :error="error" @retry="manual"
    ><template v-if="data"
      ><section class="status-panel">
        <div>
          <span class="eyebrow">当前预约状态</span>
          <h2>{{ label(data) }}</h2>
          <p v-if="data.status === 'PENDING_PAYMENT'">
            支付截止
            {{ time(data.paymentExpiresAt) }}。支付受理后仍需确认预约结果。
          </p>
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
          ><button :disabled="loading || busy" @click="manual">
            {{ loading ? "正在刷新…" : "刷新进度" }}
          </button>
        </div>
      </section>
      <p v-if="exhausted" class="notice">
        处理仍未结束。自动查询已停止，可稍后手动刷新。
      </p>
      <section v-if="paymentPage" class="panel stack">
        <h2>{{ mockEnabled ? "开发演示支付" : "支付渠道" }}</h2>
        <template v-if="mockEnabled"
          ><p class="notice">
            模拟支付仅用于开发演示，不发生真实扣款。模拟通知受理不表示预约已确认。
          </p>
          <button
            v-if="canPay"
            class="primary align-start"
            :disabled="busy"
            @click="act('pay')"
          >
            {{ busy ? "正在受理…" : "发起模拟支付" }}
          </button>
          <div v-if="activeMock" class="actions">
            <button :disabled="busy" @click="act('complete')">
              模拟渠道成功</button
            ><button :disabled="busy" @click="act('fail')">模拟渠道失败</button>
          </div>
          <p v-if="data.latestPaymentAttempt?.status === 'FAILED'">
            本次尝试失败，可在仍有支付资格时重试。
          </p></template
        >
        <p v-else>
          当前未开启可用支付渠道，请联系维护者。已有预约状态仍可查询。
        </p>
        <RouterLink :to="`/appointments/${data.id}`"
          >查看预约凭证与就诊安排</RouterLink
        >
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
          <h2>支付与退款</h2>
          <dl class="facts">
            <dt>挂号费</dt>
            <dd>¥{{ money(data.amount) }} {{ data.currency }}</dd>
            <dt>支付状态</dt>
            <dd>
              {{ paymentNames[data.paymentStatus] || data.paymentStatus }}
            </dd>
            <dt>支付截止</dt>
            <dd>{{ time(data.paymentExpiresAt) }}</dd>
            <template v-if="data.refund"
              ><dt>退款金额</dt>
              <dd>¥{{ money(data.refund.amount) }}</dd>
              <dt>退款状态</dt>
              <dd>
                {{ data.paymentStatus === "REFUNDED" ? "已完成" : "处理中" }}
              </dd>
              <dt>退款原因</dt>
              <dd>
                {{ refundReasons[data.refund.reason] || data.refund.reason }}
              </dd>
              <dt>完成时间</dt>
              <dd>{{ time(data.refund.completedAt) }}</dd></template
            >
          </dl>
          <p class="notice warning">
            已确认预约仅在距离开诊严格超过24小时时可取消；恰好24小时不允许。
          </p>
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
