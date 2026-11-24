<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { api } from "../api";
import { message } from "../api/client";
import { useRequest } from "../composables/useRequest";
import RequestState from "../components/RequestState.vue";
import { useAuth } from "../stores/auth";
import type { User } from "../api/types";

const auth = useAuth();
const { data, loading, error, run } = useRequest<User | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const selected = ref<File | null>(null);
const preview = ref("");
const busy = ref(false);
const actionError = ref("");
const success = ref("");

const load = () =>
  run(async () => {
    const user = await api.me();
    auth.applyProfile(user);
    return user;
  });

function chooseFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] ?? null;
  clearSelection();
  actionError.value = "";
  success.value = "";
  if (!file) return;
  if (
    file.type &&
    !["image/jpeg", "image/png", "image/webp"].includes(file.type)
  ) {
    actionError.value = "仅支持 JPEG、PNG 或 WebP 图片。";
    input.value = "";
    return;
  }
  if (file.size > 2 * 1024 * 1024) {
    actionError.value = "图片不能超过 2 MB。";
    input.value = "";
    return;
  }
  selected.value = file;
  preview.value = URL.createObjectURL(file);
}

function clearSelection() {
  if (preview.value) URL.revokeObjectURL(preview.value);
  preview.value = "";
  selected.value = null;
  if (fileInput.value) fileInput.value.value = "";
}

async function upload() {
  if (busy.value) return;
  actionError.value = "";
  success.value = "";
  if (!selected.value) {
    actionError.value = "请先选择一张图片。";
    fileInput.value?.focus();
    return;
  }
  busy.value = true;
  try {
    const user = await api.uploadAvatar(selected.value);
    data.value = user;
    auth.applyProfile(user);
    success.value = "头像已更新。";
    clearSelection();
  } catch (e) {
    actionError.value = message(e);
  } finally {
    busy.value = false;
  }
}

async function remove() {
  if (busy.value) return;
  actionError.value = "";
  success.value = "";
  busy.value = true;
  try {
    const user = await api.removeAvatar();
    data.value = user;
    auth.applyProfile(user);
    success.value = "头像已删除。";
    clearSelection();
  } catch (e) {
    actionError.value = message(e);
  } finally {
    busy.value = false;
  }
}

const shownAvatar = computed(
  () => preview.value || data.value?.avatarUrl || "",
);
const initial = computed(
  () => data.value?.email.trim().charAt(0).toUpperCase() || "?",
);

onMounted(load);
onBeforeUnmount(() => {
  if (preview.value) URL.revokeObjectURL(preview.value);
});
</script>
<template>
  <h1>个人信息</h1>
  <RequestState :loading="loading" :error="error" @retry="load">
    <section v-if="data" class="panel">
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
    </section>
    <section v-if="data" class="panel avatar-panel">
      <h2>头像</h2>
      <form class="avatar-editor" :aria-busy="busy" @submit.prevent="upload">
        <div class="avatar-preview">
          <img
            v-if="shownAvatar"
            class="profile-avatar"
            :src="shownAvatar"
            alt="当前头像"
            width="112"
            height="112"
          />
          <span
            v-else
            class="profile-avatar avatar-fallback"
            aria-hidden="true"
            >{{ initial }}</span
          >
        </div>
        <div class="avatar-controls">
          <label for="avatar-file">选择头像图片</label>
          <input
            id="avatar-file"
            ref="fileInput"
            name="file"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            aria-describedby="avatar-help"
            @change="chooseFile"
          />
          <p id="avatar-help" class="muted">
            支持 JPEG、PNG、WebP，大小不超过 2 MB。
          </p>
          <p v-if="selected" class="muted avatar-file-name">
            已选择：{{ selected.name }}
          </p>
          <div class="actions">
            <button class="primary" type="submit" :disabled="busy">
              {{ busy ? "处理中…" : "上传头像" }}
            </button>
            <button
              v-if="data.avatarUrl"
              class="danger"
              type="button"
              :disabled="busy"
              @click="remove"
            >
              删除头像
            </button>
          </div>
          <p v-if="actionError" class="notice error" role="alert">
            {{ actionError }}
          </p>
          <p v-if="success" class="notice" role="status">{{ success }}</p>
        </div>
      </form>
    </section>
  </RequestState>
</template>
