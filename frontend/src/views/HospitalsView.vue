<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { api } from "../api";
import { useRequest } from "../composables/useRequest";
import RequestState from "../components/RequestState.vue";
import type { Hospital } from "../api/types";
const { data, loading, error, run } = useRequest<Hospital[]>([]);
const keyword = ref("");
const shown = computed(() =>
  data.value.filter((h) => h.name.includes(keyword.value.trim())),
);
const load = () => run(api.hospitals);
onMounted(load);
</script>
<template>
  <h1>预约挂号</h1>
  <p class="lead">选择医院，查看科室、医生和预约场次。</p>
  <label class="search-field"
    >医院名称<input v-model="keyword" placeholder="筛选当前已加载医院" /></label
  ><RequestState
    :loading="loading"
    :error="error"
    :empty="!shown.length"
    :empty-text="keyword ? '没有符合名称的医院' : '暂无医院资料'"
    @retry="load"
    ><div class="card-grid">
      <article v-for="h in shown" :key="h.id" class="panel stack">
        <h2>{{ h.name }}</h2>
        <p class="muted">{{ h.address }}</p>
        <RouterLink
          class="button primary align-start"
          :to="`/booking/hospitals/${h.id}`"
          >选择医院</RouterLink
        >
      </article>
    </div></RequestState
  >
</template>
