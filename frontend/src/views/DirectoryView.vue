<script setup lang="ts">
import { ref, watch } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import type { Department, Doctor, Hospital } from "../api/types";
import RequestState from "../components/RequestState.vue";
import { useRequest } from "../composables/useRequest";
const route = useRoute(),
  departmentId = ref("");
const { data, loading, error, run } = useRequest<{
  hospital: Hospital | undefined;
  departments: Department[];
  doctors: Doctor[];
  selected: string;
}>({ hospital: undefined, departments: [], doctors: [], selected: "" });
function load() {
  const hospitalId = String(route.params.hospitalId),
    selected = departmentId.value;
  return run(async () => {
    const [hospitals, departments] = await Promise.all([
      api.hospitals(),
      api.departments(hospitalId),
    ]);
    const chosen =
      departments.find((d) => d.id === selected)?.id ||
      departments[0]?.id ||
      "";
    return {
      hospital: hospitals.find((h) => h.id === hospitalId),
      departments,
      selected: chosen,
      doctors: chosen ? await api.doctors(chosen) : [],
    };
  });
}
watch(
  () => route.params.hospitalId,
  () => {
    departmentId.value = "";
    void load();
  },
  { immediate: true },
);
watch(departmentId, load);
</script>
<template>
  <RouterLink to="/booking">医院目录</RouterLink
  ><RequestState :loading="loading" :error="error" @retry="load"
    ><h1>{{ data.hospital?.name || "医院资料不可用" }}</h1>
    <p class="lead">{{ data.hospital?.address }}</p>
    <div class="directory-layout">
      <aside class="panel department-nav">
        <h2>选择科室</h2>
        <button
          v-for="d in data.departments"
          :key="d.id"
          :aria-pressed="data.selected === d.id"
          @click="departmentId = d.id"
        >
          {{ d.name }}
        </button>
        <p v-if="!data.departments.length">暂无科室资料</p>
      </aside>
      <section class="stack">
        <article
          v-for="doctor in data.doctors"
          :key="doctor.id"
          class="panel doctor-card"
        >
          <span class="avatar" aria-hidden="true">{{ doctor.name[0] }}</span>
          <div class="grow">
            <h2>
              {{ doctor.name }} <span class="tag">{{ doctor.title }}</span>
            </h2>
            <p>{{ doctor.introduction || "暂无医生简介" }}</p>
            <small>医生编号 {{ doctor.doctorNo }}</small>
          </div>
          <RouterLink
            class="button primary"
            :to="{
              path: `/booking/doctors/${doctor.id}`,
              query: {
                hospitalId: route.params.hospitalId,
                departmentId: data.selected,
              },
            }"
            >查看场次</RouterLink
          >
        </article>
        <p v-if="!data.doctors.length" class="panel feedback">
          当前科室暂无医生，请选择其他科室。
        </p>
      </section>
    </div></RequestState
  >
</template>
