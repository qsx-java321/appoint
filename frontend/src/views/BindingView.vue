<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { api } from "../api";
import { message } from "../api/client";
import { useCatalog } from "../composables/useCatalog";
import RequestState from "../components/RequestState.vue";
const {
  hospitalId,
  departmentId,
  hospitals,
  departments,
  doctors,
  loading,
  error,
  load,
  refresh,
} = useCatalog();
const doctorId = ref(""),
  userId = ref(""),
  busy = ref(false),
  actionError = ref(""),
  result = ref<{ doctorId: string; userId: string } | null>(null);
watch([hospitalId, departmentId], () => {
  doctorId.value = "";
  result.value = null;
});
async function bind() {
  const doctor = doctors.value.find((d) => d.id === doctorId.value);
  if (
    busy.value ||
    !doctor ||
    !window.confirm(
      `首次绑定${doctor.name}（${doctor.doctorNo}）与账号ID ${userId.value}？当前无法查询目标账号邮箱；请先核对ID。绑定后不支持解绑、换绑。`,
    )
  )
    return;
  busy.value = true;
  actionError.value = "";
  result.value = null;
  try {
    result.value = await api.bind(doctor.id, userId.value.trim());
  } catch (e) {
    actionError.value = message(e);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <h1>医生账号首次绑定</h1>
  <p class="lead">选择医生档案，再填写已存在的登录账号ID。</p>
  <p>
    <RouterLink to="/admin/doctor-accounts"
      >查看医生账号及当前绑定关系</RouterLink
    >
  </p>
  <RequestState :loading="loading" :error="error" @retry="refresh"
    ><section class="panel">
      <form class="form settings-form" @submit.prevent="bind">
        <div class="parent-fields">
          <label
            >所属医院<select v-model="hospitalId" :disabled="busy">
              <option v-for="h in hospitals" :key="h.id" :value="h.id">
                {{ h.name }}
              </option>
            </select></label
          ><label
            >所属科室<select v-model="departmentId" :disabled="busy">
              <option v-for="d in departments" :key="d.id" :value="d.id">
                {{ d.name }}
              </option>
            </select></label
          >
        </div>
        <label
          >医生档案<select v-model="doctorId" required :disabled="busy">
            <option value="">请选择医生</option>
            <option v-for="d in doctors" :key="d.id" :value="d.id">
              {{ d.name }} · {{ d.doctorNo }}
            </option>
          </select></label
        ><label
          >已存在的账号ID<input
            v-model="userId"
            required
            inputmode="numeric"
            pattern="[1-9][0-9]*"
            maxlength="19"
            :disabled="busy"
          /><small
            >由目标账号从“个人信息”提供，账号ID与就诊人ID不同。</small
          ></label
        >
        <p class="notice">
          仅支持首次绑定。绑定成功后请医生重新登录，取得工作台权限。
        </p>
        <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
        <button class="primary" :disabled="busy || !doctors.length">
          {{ busy ? "正在绑定…" : "核对并绑定账号" }}
        </button>
      </form>
      <div v-if="result" class="notice" role="status">
        <h2>本次绑定成功</h2>
        <p>医生ID {{ result.doctorId }} · 账号ID {{ result.userId }}</p>
        <p>请医生重新登录；可返回医生账号查询页面查看最新绑定关系。</p>
      </div>
    </section></RequestState
  >
</template>
