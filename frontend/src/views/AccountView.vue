<script setup lang="ts">
import { onMounted } from "vue";
import { api } from "../api";
import { useRequest } from "../composables/useRequest";
import RequestState from "../components/RequestState.vue";
import type { User } from "../api/types";
const { data, loading, error, run } = useRequest<User | null>(null);
const load = () => run(api.me);
onMounted(load);
</script>
<template>
  <h1>个人信息</h1>
  <RequestState :loading="loading" :error="error" @retry="load"
    ><section v-if="data" class="panel">
      <dl class="facts">
        <dt>账号ID</dt>
        <dd>{{ data.id }}</dd>
        <dt>登录邮箱</dt>
        <dd>{{ data.email }}</dd>
        <dt>当前数据库角色</dt>
        <dd>{{ data.roles.join("、") }}</dd>
      </dl>
      <p class="notice">
        获得新角色后请退出并重新登录，菜单和访问权限才会更新。
      </p>
    </section></RequestState
  >
</template>
