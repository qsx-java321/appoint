<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import type { Patient } from "../api/types";
import { message } from "../api/client";
import { useRequest } from "../composables/useRequest";
import PatientForm from "../components/PatientForm.vue";
import RequestState from "../components/RequestState.vue";
const route = useRoute(),
  router = useRouter();
const { data, loading, error, run } = useRequest<Patient[]>([]);
const editing = ref<Patient | "new" | null>(route.query.new ? "new" : null),
  actionError = ref(""),
  busy = ref(false);
const load = () => run(api.patients);
async function saved(patient: Patient) {
  editing.value = null;
  if (route.query.returnTo === "confirm")
    await router.push({
      path: "/booking/confirm",
      query: { patientId: patient.id },
    });
  else await load();
}
async function toggle(patient: Patient) {
  if (
    busy.value ||
    !window.confirm(
      `${patient.enabled ? "停用" : "启用"}${patient.name}？已有预约不受影响。`,
    )
  )
    return;
  busy.value = true;
  actionError.value = "";
  try {
    await api.patientStatus(patient.id, !patient.enabled);
    await load();
  } catch (e) {
    actionError.value = message(e);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="section-heading">
    <div>
      <h1>就诊人</h1>
      <p class="lead">维护预约使用的资料，修改不会重写历史凭证。</p>
    </div>
    <button class="primary" @click="editing = 'new'">新增就诊人</button>
  </div>
  <p v-if="actionError" class="notice error" role="alert">{{ actionError }}</p>
  <section v-if="editing" class="panel">
    <h2>{{ editing === "new" ? "新增就诊人" : "编辑资料" }}</h2>
    <PatientForm
      :key="editing === 'new' ? 'new' : editing.id"
      :patient="editing === 'new' ? undefined : editing"
      @saved="saved"
      @cancel="editing = null"
    />
  </section>
  <RequestState
    v-else
    :loading="loading"
    :error="error"
    :empty="!data.length"
    empty-text="请先新增一位就诊人"
    @retry="load"
    ><div class="card-grid">
      <section v-for="p in data" :key="p.id" class="panel stack">
        <div class="section-heading">
          <h2>{{ p.name }}</h2>
          <span class="tag">{{ p.enabled ? "已启用" : "已停用" }}</span>
        </div>
        <dl class="facts">
          <dt>身份证</dt>
          <dd>{{ p.documentNoMasked }}</dd>
          <dt>联系电话</dt>
          <dd>{{ p.contactPhone }}</dd>
          <dt>联系邮箱</dt>
          <dd>{{ p.contactEmail }}</dd>
        </dl>
        <div class="actions">
          <button @click="editing = p">编辑资料</button
          ><button :disabled="busy" @click="toggle(p)">
            {{ p.enabled ? "停用" : "启用" }}
          </button>
        </div>
      </section>
    </div></RequestState
  >
</template>
