<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api } from "../api";
import type { NotificationDetail, NotificationSummary } from "../api/types";
import RequestState from "../components/RequestState.vue";
import { useRequest } from "../composables/useRequest";
import { time } from "../lib/presentation";

const {
  data: items,
  loading: listLoading,
  error: listError,
  run: loadList,
} = useRequest<NotificationSummary[]>([]);
const {
  data: selected,
  loading: detailLoading,
  error: detailError,
  run: loadDetail,
  invalidate,
} = useRequest<NotificationDetail | null>(null);
const selectedId = ref<string | null>(null);

async function load() {
  await loadList(() => api.notifications());
  if (selectedId.value && !items.value.some((n) => n.id === selectedId.value))
    clearSelection();
}
function clearSelection() {
  selectedId.value = null;
  selected.value = null;
  invalidate();
}
async function open(item: NotificationSummary) {
  selectedId.value = item.id;
  selected.value = null;
  await loadDetail(() => api.notification(item.id));
}
async function retryDetail() {
  const item = items.value.find((n) => n.id === selectedId.value);
  if (item) await open(item);
}
onMounted(load);
</script>
<template>
  <div class="section-heading">
    <div>
      <h1>消息中心</h1>
      <p class="lead">预约确认、签到和接诊进度会在这里更新。</p>
    </div>
    <button type="button" :disabled="listLoading" @click="load">
      {{ listLoading ? "正在刷新…" : "刷新" }}
    </button>
  </div>
  <RequestState
    :loading="listLoading"
    :error="listError"
    :empty="!items.length"
    empty-text="暂无消息"
    @retry="load"
    ><div class="detail-grid">
      <div class="notification-list" aria-label="消息列表">
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          class="notification-item"
          :aria-pressed="selectedId === item.id"
          @click="open(item)"
        >
          <strong>{{ item.title }}</strong>
          <span class="muted"
            >{{ item.patientName }} · {{ time(item.occurredAt) }}</span
          >
        </button>
      </div>
      <section class="panel stack" aria-live="polite">
        <p v-if="detailLoading" class="feedback" role="status">
          正在加载消息详情…
        </p>
        <div v-else-if="detailError" class="notice error" role="alert">
          <p>{{ detailError }}</p>
          <button type="button" @click="retryDetail">重试</button>
        </div>
        <template v-else-if="selected">
          <span class="eyebrow">{{ selected.title }}</span>
          <h2>{{ selected.patientName }}</h2>
          <p class="muted">{{ time(selected.occurredAt) }}</p>
          <p>{{ selected.detail }}</p>
          <RouterLink
            class="button primary align-start"
            :to="`/appointments/${selected.appointmentId}`"
            >查看预约</RouterLink
          >
        </template>
        <p v-else class="feedback">从左侧选择一条消息查看详情。</p>
      </section>
    </div></RequestState
  >
</template>
