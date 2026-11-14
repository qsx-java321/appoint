<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import { message } from "../api/client";
import { useAuth } from "../stores/auth";
import { safeReturn } from "../lib/session";
const route = useRoute(),
  router = useRouter(),
  auth = useAuth();
const register = computed(() => route.path === "/register");
const email = ref(""),
  password = ref(""),
  visible = ref(false),
  busy = ref(false),
  error = ref(""),
  success = ref("");
async function submit() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    if (register.value) {
      await api.register(email.value.trim().toLowerCase(), password.value);
      password.value = "";
      success.value = "注册成功，请登录。";
      await router.replace({
        path: "/login",
        query: { redirect: safeReturn(route.query.redirect) },
      });
    } else {
      await auth.login(email.value, password.value);
      password.value = "";
      await router.replace(safeReturn(route.query.redirect));
    }
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="auth-layout">
    <section class="auth-copy">
      <span class="eyebrow">医院预约服务</span>
      <h1>预约清楚一点，<br />就诊从容一点。</h1>
      <p>选择医院、科室和医生，查看今天至后天的预约场次。</p>
      <p>维护就诊人，持续查看预约、签到和就诊进度。</p>
    </section>
    <section class="panel">
      <div class="section-heading">
        <h2>{{ register ? "注册账号" : "欢迎登录" }}</h2>
        <RouterLink
          :to="{
            path: register ? '/login' : '/register',
            query: { redirect: safeReturn(route.query.redirect) },
          }"
          >{{ register ? "已有账号" : "注册账号" }}</RouterLink
        >
      </div>
      <p v-if="route.query.unavailable" class="notice">
        暂时无法验证会话，请检查服务连接后重试。
      </p>
      <form class="form" @submit.prevent="submit">
        <label
          >邮箱<input
            v-model="email"
            type="email"
            required
            autocomplete="username" /></label
        ><label
          >密码<span class="input-row"
            ><input
              aria-label="密码"
              v-model="password"
              :type="visible ? 'text' : 'password'"
              required
              :minlength="register ? 8 : undefined"
              :maxlength="register ? 64 : undefined"
              :autocomplete="register ? 'new-password' : 'current-password'"
            /><button
              type="button"
              :aria-label="visible ? '隐藏密码' : '显示密码'"
              @click="visible = !visible"
            >
              {{ visible ? "隐藏" : "显示" }}
            </button></span
          ><small v-if="register">8–64个字符</small></label
        >
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="success" class="notice" role="status">{{ success }}</p>
        <button class="primary" :disabled="busy">
          {{ busy ? "正在提交…" : register ? "注册账号" : "登录" }}
        </button>
        <p class="muted">
          {{
            register
              ? "注册账号默认获得普通用户身份。"
              : "请先登录。医生与管理员沿用同一入口。"
          }}
        </p>
      </form>
    </section>
  </div>
</template>
