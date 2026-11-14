<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api } from "../api";
import { message } from "../api/client";
import { useRequest } from "../composables/useRequest";
import RequestState from "../components/RequestState.vue";
import type { BookingSettings } from "../api/types";
import { time } from "../lib/presentation";
const { data, loading, error, run } = useRequest<BookingSettings | null>(null);
const capacity = ref(20),
  fee = ref("10.00"),
  busy = ref(false),
  actionError = ref(""),
  notice = ref("");
async function load() {
  await run(api.settings);
  if (data.value) {
    capacity.value = data.value.capacityPerSession;
    fee.value = data.value.registrationFee;
  }
}
async function save() {
  if (
    busy.value ||
    !window.confirm(
      `确认每场容量${capacity.value}人、挂号费${fee.value}元？仅影响之后首次生成的场次，已有场次和订单保留原设置。`,
    )
  )
    return;
  busy.value = true;
  actionError.value = "";
  notice.value = "";
  try {
    data.value = await api.saveSettings(capacity.value, fee.value);
    notice.value = "统一预约设置已保存。";
  } catch (e) {
    actionError.value = message(e);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <h1>统一预约设置</h1>
  <p class="lead">容量和挂号费应用于之后首次生成的场次。</p>
  <RequestState :loading="loading" :error="error" @retry="load"
    ><section class="panel">
      <form class="form settings-form" @submit.prevent="save">
        <label
          >每场容量<input
            v-model.number="capacity"
            type="number"
            required
            min="1"
            max="2147483647"
            step="1" /></label
        ><label
          >挂号费（元）<input
            v-model="fee"
            type="number"
            required
            min="0.01"
            max="99999999.99"
            step="0.01"
        /></label>
        <p>币种：人民币 CNY</p>
        <p class="notice warning">
          已有场次保留原容量和费用，已有订单保留原快照。同一天的不同场次可能使用不同设置。
        </p>
        <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <button class="primary" :disabled="busy">
          {{ busy ? "正在保存…" : "核对并保存设置" }}</button><small
          >最近更新 {{ time(data?.updatedAt) }}</small
        >
      </form>
    </section></RequestState
  >
</template>
