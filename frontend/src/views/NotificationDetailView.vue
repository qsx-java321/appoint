<script setup lang="ts">
import { watch } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import type { NotificationDetail } from "../api/types";
import RequestState from "../components/RequestState.vue";
import { useRequest } from "../composables/useRequest";
import { time } from "../lib/presentation";

const route = useRoute();
const { data, loading, error, run, invalidate } =
  useRequest<NotificationDetail | null>(null);

function load() {
  return run(() => api.notification(String(route.params.id)));
}

watch(
  () => route.params.id,
  (id) => {
    invalidate();
    data.value = null;
    if (id) void load();
  },
  { immediate: true },
);
</script>
<template>
  <RouterLink to="/notifications">返回消息中心</RouterLink>
  <div class="section-heading">
    <div>
      <h1>消息详情</h1>
      <p class="lead">这里保存的是消息生成时的就诊信息。</p>
    </div>
    <button type="button" :disabled="loading" @click="load">
      {{ loading ? "正在刷新…" : "刷新" }}
    </button>
  </div>
  <RequestState :loading="loading && !data" :error="error" @retry="load"
    ><section v-if="data" class="panel stack notification-detail">
      <span class="eyebrow">{{ data.title }}</span>
      <h2>{{ data.patientName }}</h2>
      <p class="muted">{{ time(data.occurredAt) }}</p>
      <p class="notification-detail-text">{{ data.detail }}</p>
      <div class="actions">
        <RouterLink
          class="button primary"
          :to="`/appointments/${data.appointmentId}`"
          >查看预约</RouterLink
        >
        <RouterLink class="button" to="/notifications">返回消息列表</RouterLink>
      </div>
    </section></RequestState
  >
</template>
