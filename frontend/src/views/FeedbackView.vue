<script setup lang="ts">
import { useRoute } from "vue-router";
const route = useRoute();
</script>
<template>
  <section class="panel feedback">
    <h1>
      {{ route.path === "/forbidden" ? "无权访问此页面" : "找不到此页面" }}
    </h1>
    <p>请确认当前账号和访问地址。</p>
    <RouterLink class="button primary" to="/booking">返回预约挂号</RouterLink>
  </section>
</template>
