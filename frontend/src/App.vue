<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuth } from "./stores/auth";

const route = useRoute(),
  router = useRouter(),
  auth = useAuth();
const admin = computed(() => route.path.startsWith("/admin"));
const staff = computed(() => admin.value || route.path === "/doctor");
const publicPage = computed(
  () => route.path === "/login" || route.path === "/register",
);
const avatarUrl = computed(
  () => auth.profile?.avatarUrl || auth.session?.user.avatarUrl || "",
);
const avatarInitial = computed(
  () => auth.session?.user.email.trim().charAt(0).toUpperCase() || "?",
);
const avatarFailed = ref(false);
watch(avatarUrl, () => {
  avatarFailed.value = false;
});
watch(
  () => route.fullPath,
  () => document.querySelector(".account-menu")?.removeAttribute("open"),
);
let expiryTimer: ReturnType<typeof setTimeout> | undefined;
watch(
  () => auth.session?.expiresAt,
  (expires) => {
    clearTimeout(expiryTimer);
    if (expires)
      expiryTimer = setTimeout(
        () => auth.logout(),
        Math.max(0, expires - Date.now()),
      );
  },
  { immediate: true },
);
onUnmounted(() => clearTimeout(expiryTimer));
watch(
  () => auth.authenticated,
  (value) => {
    if (!value && !route.meta.public)
      void router.replace({
        path: "/login",
        query: { redirect: route.fullPath },
      });
  },
);
function logout() {
  auth.logout();
  void router.replace("/login");
}
</script>
<template>
  <a class="skip-link" href="#main">跳到主要内容</a>
  <header class="header">
    <RouterLink class="brand" to="/booking"
      ><span class="brand-mark" aria-hidden="true">＋</span
      ><span>医院预约<small>便捷预约 · 安心就诊</small></span></RouterLink
    >
    <nav
      v-if="!publicPage && auth.authenticated"
      aria-label="服务导航"
      class="main-nav"
    >
      <RouterLink v-if="staff" to="/booking">返回用户端</RouterLink>
      <template v-else
        ><RouterLink
          :class="{ 'is-current': route.path.startsWith('/booking') }"
          to="/booking"
          >预约挂号</RouterLink
        ><RouterLink
          :class="{ 'is-current': route.path.startsWith('/appointments') }"
          to="/appointments"
          >我的预约</RouterLink
        ><RouterLink to="/patients">就诊人</RouterLink></template
      ><RouterLink
        :class="{ 'is-current': route.path.startsWith('/notifications') }"
        to="/notifications"
        >消息中心</RouterLink
      >
    </nav>
    <details v-if="!publicPage && auth.authenticated" class="account-menu">
      <summary>
        <img
          v-if="avatarUrl && !avatarFailed"
          class="menu-avatar"
          :src="avatarUrl"
          alt=""
          width="28"
          height="28"
          @error="avatarFailed = true"
        />
        <span
          v-else
          class="menu-avatar menu-avatar-fallback"
          aria-hidden="true"
          >{{ avatarInitial }}</span
        >
        <span class="menu-email">{{ auth.session?.user.email }}</span>
      </summary>
      <div class="menu-items">
        <RouterLink to="/account">个人信息</RouterLink>
        <RouterLink v-if="auth.hasRole('DOCTOR')" to="/doctor"
          >医生工作台</RouterLink
        >
        <RouterLink v-if="auth.hasRole('ADMIN')" to="/admin/catalog"
          >管理工作台</RouterLink
        >
        <button type="button" @click="logout">退出登录</button>
      </div>
    </details>
  </header>
  <div :class="['shell', { 'admin-shell': admin }]">
    <aside v-if="admin" class="admin-nav" aria-label="管理菜单">
      <strong>管理工作台</strong
      ><RouterLink to="/admin/catalog">医院科室医生</RouterLink
      ><RouterLink to="/admin/booking-settings">统一预约设置</RouterLink
      ><RouterLink to="/admin/doctor-accounts">医生账号查询</RouterLink
      ><RouterLink to="/admin/doctor-accounts/bind">医生账号绑定</RouterLink>
    </aside>
    <main id="main"><RouterView /></main>
  </div>
  <footer class="footer">
    医院预约服务 · 业务时间按北京时间（Asia/Shanghai）
  </footer>
</template>
