import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { api } from "../api";
import { clearSession, readSession, writeSession } from "../lib/session";
import type { Role, User } from "../api/types";

export const useAuth = defineStore("auth", () => {
  const session = ref(readSession());
  const profile = ref<User | null>(null);
  const authenticated = computed(
    () => !!session.value && session.value.expiresAt > Date.now(),
  );
  let checkedToken: string | null = null;
  let checking: Promise<void> | null = null;
  function hasRole(role: Role) {
    return session.value?.user.roles.includes(role) || false;
  }
  async function login(email: string, password: string) {
    session.value = writeSession(
      await api.login(email.trim().toLowerCase(), password),
    );
    checkedToken = session.value.token;
    profile.value = session.value.user;
  }
  async function restore() {
    session.value = readSession();
    if (!session.value || checkedToken === session.value.token) return;
    if (checking) return checking;
    const token = session.value.token;
    checking = api
      .me()
      .then((user) => {
        if (session.value?.token === token) {
          profile.value = user;
          checkedToken = token;
        }
      })
      .finally(() => {
        checking = null;
      });
    return checking;
  }
  function logout() {
    clearSession();
    session.value = null;
    profile.value = null;
    checkedToken = null;
  }
  window.addEventListener("appoint:logout", () => {
    session.value = null;
    profile.value = null;
    checkedToken = null;
  });
  return { session, profile, authenticated, hasRole, login, restore, logout };
});
