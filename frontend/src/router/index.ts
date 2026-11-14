import { createRouter, createWebHistory } from "vue-router";
import { useAuth } from "../stores/auth";
import type { Role } from "../api/types";
import { ApiError } from "../api/client";

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (_, __, saved) => saved || { top: 0 },
  routes: [
    { path: "/", redirect: "/booking" },
    {
      path: "/login",
      component: () => import("../views/AuthView.vue"),
      meta: { public: true },
    },
    {
      path: "/register",
      component: () => import("../views/AuthView.vue"),
      meta: { public: true },
    },
    { path: "/booking", component: () => import("../views/HospitalsView.vue") },
    {
      path: "/booking/hospitals/:hospitalId",
      component: () => import("../views/DirectoryView.vue"),
    },
    {
      path: "/booking/doctors/:doctorId",
      component: () => import("../views/SchedulesView.vue"),
    },
    {
      path: "/booking/confirm",
      component: () => import("../views/ConfirmView.vue"),
    },
    {
      path: "/appointments",
      component: () => import("../views/AppointmentsView.vue"),
    },
    {
      path: "/appointments/:id",
      component: () => import("../views/AppointmentView.vue"),
    },
    {
      path: "/appointments/:id/payment",
      component: () => import("../views/AppointmentView.vue"),
      meta: { payment: true },
    },
    { path: "/patients", component: () => import("../views/PatientsView.vue") },
    { path: "/account", component: () => import("../views/AccountView.vue") },
    {
      path: "/doctor",
      component: () => import("../views/DoctorView.vue"),
      meta: { role: "DOCTOR" },
    },
    { path: "/admin", redirect: "/admin/catalog" },
    {
      path: "/admin/catalog",
      component: () => import("../views/CatalogView.vue"),
      meta: { role: "ADMIN" },
    },
    {
      path: "/admin/booking-settings",
      component: () => import("../views/SettingsView.vue"),
      meta: { role: "ADMIN" },
    },
    {
      path: "/admin/doctor-accounts",
      component: () => import("../views/BindingView.vue"),
      meta: { role: "ADMIN" },
    },
    {
      path: "/forbidden",
      component: () => import("../views/FeedbackView.vue"),
    },
    {
      path: "/:pathMatch(.*)*",
      component: () => import("../views/FeedbackView.vue"),
      meta: { public: true },
    },
  ],
});
router.beforeEach(async (to) => {
  if (to.meta.public) return true;
  const auth = useAuth();
  try {
    await auth.restore();
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401)
      return {
        path: "/login",
        query: { redirect: to.fullPath, unavailable: "1" },
      };
  }
  if (!auth.authenticated)
    return { path: "/login", query: { redirect: to.fullPath } };
  if (to.meta.role && !auth.hasRole(to.meta.role as Role)) return "/forbidden";
  return true;
});
