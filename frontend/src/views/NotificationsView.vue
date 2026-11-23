<script setup lang="ts">
import { onMounted } from "vue";
import { api } from "../api";
import type { NotificationSummary } from "../api/types";
import RequestState from "../components/RequestState.vue";
import { useRequest } from "../composables/useRequest";
import { time } from "../lib/presentation";

const {
  data: items,
  loading,
  error,
  run,
} = useRequest<NotificationSummary[]>([]);
const load = () => run(() => api.notifications());
onMounted(load);
</script>
<template>
  <div class="section-heading">
    <div>
      <h1>消息中心</h1>
      <p class="lead">预约确认、签到和接诊进度会在这里更新。</p>
    </div>
    <button type="button" :disabled="loading" @click="load">
      {{ loading ? "正在刷新…" : "刷新" }}
    </button>
  </div>
  <RequestState
    :loading="loading"
    :error="error"
    :empty="!items.length"
    empty-text="暂无消息"
    @retry="load"
    ><div class="notification-list" aria-label="消息列表">
      <RouterLink
        v-for="item in items"
        :key="item.id"
        class="notification-item"
        :to="`/notifications/${item.id}`"
      >
        <span class="notification-copy">
          <strong>{{ item.title }}</strong>
          <span class="muted"
            >{{ item.patientName }} · {{ time(item.occurredAt) }}</span
          >
        </span>
        <span class="notification-arrow" aria-hidden="true">查看详情 →</span>
      </RouterLink>
    </div></RequestState
  >
</template>
