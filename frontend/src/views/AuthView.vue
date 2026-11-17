<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import { ApiError, message } from "../api/client";
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
const step = ref<"verify" | "password">("verify");
const code = ref(""),
  signupTicket = ref(""),
  verifiedEmail = ref(""),
  confirmPassword = ref("");
function resetRegistration() {
  step.value = "verify";
  signupTicket.value = "";
  password.value = "";
  confirmPassword.value = "";
  code.value = "";
}
watch(
  () => route.path,
  () => {
    resetRegistration();
    error.value = "";
    success.value = "";
  },
);
async function sendCode() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    await api.sendRegistrationCode(email.value.trim().toLowerCase());
    success.value = "验证码已发送，有效期5分钟。请在 Mailpit 查看邮件。";
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function submit() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    if (register.value) {
      if (step.value === "verify") {
        const normalized = email.value.trim().toLowerCase();
        const result = await api.verifyRegistrationCode(normalized, code.value);
        signupTicket.value = result.signupTicket;
        verifiedEmail.value = normalized;
        step.value = "password";
        code.value = "";
        return;
      }
      if (password.value !== confirmPassword.value) {
        error.value = "两次密码输入不一致。";
        return;
      }
      await api.register(
        verifiedEmail.value,
        password.value,
        signupTicket.value,
      );
      resetRegistration();
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
    if (e instanceof ApiError && e.code === 3005) resetRegistration();
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
        <p v-if="register">
          {{ step === "verify" ? "第一步：验证邮箱" : "第二步：设置密码" }}
        </p>
        <p v-if="register && step === 'password'">
          已验证邮箱：{{ verifiedEmail }}
        </p>
        <label v-if="!register || step === 'verify'"
          >邮箱<input
            v-model="email"
            type="email"
            required
            autocomplete="username"
        /></label>
        <template v-if="register && step === 'verify'">
          <button type="button" :disabled="busy || !email" @click="sendCode">
            发送验证码
          </button>
          <label
            >验证码<input
              v-model="code"
              aria-label="验证码"
              required
              pattern="[0-9]{6}"
              maxlength="6"
              inputmode="numeric"
              autocomplete="one-time-code"
          /></label>
        </template>
        <label v-if="!register || step === 'password'"
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
        <template v-if="register && step === 'password'">
          <label
            >确认密码<input
              v-model="confirmPassword"
              aria-label="确认密码"
              type="password"
              required
              minlength="8"
              maxlength="64"
              autocomplete="new-password"
          /></label>
          <button type="button" :disabled="busy" @click="resetRegistration">
            返回修改邮箱
          </button>
        </template>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="success" class="notice" role="status">{{ success }}</p>
        <button class="primary" :disabled="busy">
          {{
            busy
              ? "正在提交…"
              : register
                ? step === "verify"
                  ? "下一步"
                  : "完成注册"
                : "登录"
          }}
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
