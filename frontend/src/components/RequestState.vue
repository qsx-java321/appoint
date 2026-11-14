<script setup lang="ts">
defineProps<{
  loading?: boolean;
  error?: string;
  empty?: boolean;
  emptyText?: string;
}>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <p v-if="loading" class="feedback" role="status">正在加载，请稍候…</p>
  <div v-else-if="error" class="notice error" role="alert">
    <p>{{ error }}</p>
    <button type="button" @click="$emit('retry')">重试</button>
  </div>
  <div v-else-if="empty" class="feedback">
    <h2>{{ emptyText || "暂无记录" }}</h2>
    <slot name="empty" />
  </div>
  <slot v-else />
</template>
