<script setup lang="ts">
import { reactive, ref } from "vue";
import type { Patient, PatientInput } from "../api/types";
import { api } from "../api";
import { message } from "../api/client";
const props = defineProps<{ patient?: Patient }>();
const emit = defineEmits<{ saved: [patient: Patient]; cancel: [] }>();
const form = reactive({
  name: props.patient?.name || "",
  documentNo: "",
  contactPhone: props.patient?.contactPhone || "",
  contactEmail: props.patient?.contactEmail || "",
});
const changeDocument = ref(!props.patient),
  busy = ref(false),
  error = ref("");
async function save() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    const data: Partial<PatientInput> = {
      name: form.name.trim(),
      contactPhone: form.contactPhone.trim(),
      contactEmail: form.contactEmail.trim().toLowerCase(),
    };
    if (changeDocument.value) {
      data.documentType = "RESIDENT_ID_CARD";
      data.documentNo = form.documentNo.trim().toUpperCase();
    }
    const patient = await api.savePatient(data, props.patient?.id);
    form.documentNo = "";
    emit("saved", patient);
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <form class="form" @submit.prevent="save">
    <div class="form-grid">
      <label>姓名<input v-model="form.name" required maxlength="64" /></label
      ><label>证件类型<input value="居民身份证" readonly /></label
      ><label v-if="changeDocument"
        >身份证号码<input
          aria-label="身份证号码"
          v-model="form.documentNo"
          required
          minlength="18"
          maxlength="18"
          autocomplete="off"
        /><small>完整证件号码由服务端校验，不代表外部实名认证。</small></label
      >
      <div v-else class="form">
        <span>身份证号码</span>
        <p>{{ patient?.documentNoMasked }}</p>
      </div>
      <div v-if="patient" class="form">
        <button
          type="button"
          @click="
            changeDocument = !changeDocument;
            form.documentNo = '';
          "
        >
          {{ changeDocument ? "保留原证件号码" : "更换证件号码" }}
        </button>
      </div>
      <label
        >联系电话<input
          v-model="form.contactPhone"
          type="tel"
          required
          maxlength="32" /></label
      ><label
        >联系邮箱<input
          v-model="form.contactEmail"
          type="email"
          required
          maxlength="120"
      /></label>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="actions">
      <button class="primary" :disabled="busy">
        {{ busy ? "正在保存…" : "保存就诊人" }}</button><button
        type="button"
        :disabled="busy"
        @click="emit('cancel')"
      >
        取消编辑
      </button>
    </div>
  </form>
</template>
