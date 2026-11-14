<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { api } from "../api";
import { message } from "../api/client";
import { useCatalog } from "../composables/useCatalog";
import RequestState from "../components/RequestState.vue";
type Kind = "hospitals" | "departments" | "doctors";
interface Row {
  id: string;
  name: string;
  address?: string;
  title?: string;
  introduction?: string | null;
  doctorNo?: string;
}
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
const kind = ref<Kind>("hospitals"),
  editing = ref<Row | null>(null),
  formOpen = ref(false),
  busy = ref(false),
  actionError = ref(""),
  notice = ref("");
const form = reactive({ name: "", address: "", title: "", introduction: "" });
const names = { hospitals: "医院", departments: "科室", doctors: "医生" };
const rows = computed<Row[]>(() =>
  kind.value === "hospitals"
    ? hospitals.value
    : kind.value === "departments"
      ? departments.value
      : doctors.value,
);
watch([kind, hospitalId, departmentId], () => {
  formOpen.value = false;
  actionError.value = "";
  notice.value = "";
});
function edit(row: Row | null) {
  editing.value = row;
  Object.assign(form, {
    name: row?.name || "",
    address: row?.address || "",
    title: row?.title || "",
    introduction: row?.introduction || "",
  });
  formOpen.value = true;
  actionError.value = "";
}
async function save() {
  if (busy.value) return;
  actionError.value = "";
  notice.value = "";
  if (
    kind.value === "doctors" &&
    editing.value?.introduction &&
    !form.introduction.trim()
  ) {
    actionError.value = "当前接口不支持清空医生简介，请保留原文或填写新简介。";
    return;
  }
  const payload: Record<string, string> = { name: form.name.trim() };
  if (kind.value === "hospitals") payload.address = form.address.trim();
  if (kind.value === "doctors") {
    payload.title = form.title.trim();
    if (form.introduction.trim())
      payload.introduction = form.introduction.trim();
  }
  busy.value = true;
  try {
    await api.saveCatalog(
      kind.value,
      editing.value?.id || null,
      kind.value === "departments" ? hospitalId.value : departmentId.value,
      payload,
    );
    formOpen.value = false;
    await refresh();
    notice.value = "目录资料已保存。";
  } catch (e) {
    actionError.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function remove(row: Row) {
  if (
    busy.value ||
    !window.confirm(
      `删除${row.name}？仍有关联资料或历史业务引用时会被拒绝，不级联删除。`,
    )
  )
    return;
  busy.value = true;
  actionError.value = "";
  notice.value = "";
  try {
    await api.deleteCatalog(kind.value, row.id);
    await refresh();
    notice.value = "资料已删除。";
  } catch (e) {
    actionError.value = message(e);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <h1>医院、科室与医生</h1>
  <p class="lead">逐级维护目录资料</p>
  <div class="actions">
    <button
      v-for="k in ['hospitals', 'departments', 'doctors'] as const"
      :key="k"
      :disabled="busy"
      :aria-pressed="kind === k"
      @click="kind = k"
    >
      {{ names[k] }}
    </button>
  </div>
  <div v-if="kind !== 'hospitals'" class="parent-fields">
    <label
      >所属医院<select v-model="hospitalId" :disabled="busy">
        <option v-for="h in hospitals" :key="h.id" :value="h.id">
          {{ h.name }}
        </option>
      </select></label
    ><label v-if="kind === 'doctors'"
      >所属科室<select v-model="departmentId" :disabled="busy">
        <option v-for="d in departments" :key="d.id" :value="d.id">
          {{ d.name }}
        </option>
      </select></label
    >
  </div>
  <p v-if="actionError" class="notice error" role="alert">{{ actionError }}</p>
  <p v-if="notice" class="notice" role="status">{{ notice }}</p>
  <RequestState :loading="loading" :error="error" @retry="refresh"
    ><section class="panel">
      <div class="section-heading">
        <h2>{{ names[kind] }}列表</h2>
        <button
          class="primary"
          :disabled="
            busy ||
            (kind === 'departments' && !hospitalId) ||
            (kind === 'doctors' && !departmentId)
          "
          @click="edit(null)"
        >
          新增{{ names[kind] }}
        </button>
      </div>
      <form v-if="formOpen" class="form" @submit.prevent="save">
        <label
          >名称<input
            v-model="form.name"
            required
            :maxlength="kind === 'doctors' ? 64 : 100" /></label
        ><label v-if="kind === 'hospitals'"
          >地址<input v-model="form.address" required maxlength="200" /></label
        ><template v-if="kind === 'doctors'"
          ><p v-if="editing" class="muted">
            医生编号 {{ editing.doctorNo }}（系统生成，不可编辑）
          </p>
          <label
            >职称<input v-model="form.title" required maxlength="64" /></label
          ><label
            >简介<textarea
              v-model="form.introduction"
              maxlength="500"
            ></textarea
            ><small>当前接口不支持清空已有简介或更换所属科室。</small></label
          ></template
        >
        <div class="actions">
          <button class="primary" :disabled="busy">
            {{ busy ? "正在保存…" : "保存资料" }}</button><button
            type="button"
            :disabled="busy"
            @click="formOpen = false"
          >
            取消编辑
          </button>
        </div>
      </form>
      <table v-else-if="rows.length" class="data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>
              {{
                kind === "hospitals"
                  ? "地址"
                  : kind === "doctors"
                    ? "职称 / 编号"
                    : "所属医院"
              }}
            </th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td data-label="名称">{{ row.name }}</td>
            <td
              :data-label="
                kind === 'hospitals'
                  ? '地址'
                  : kind === 'doctors'
                    ? '职称 / 编号'
                    : '所属医院'
              "
            >
              {{
                kind === "hospitals"
                  ? row.address
                  : kind === "doctors"
                    ? `${row.title} · ${row.doctorNo}`
                    : hospitals.find((h) => h.id === hospitalId)?.name
              }}
            </td>
            <td data-label="操作">
              <div class="actions">
                <button :disabled="busy" @click="edit(row)">编辑</button
                ><button class="danger" :disabled="busy" @click="remove(row)">
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="feedback">暂无{{ names[kind] }}资料</p>
    </section></RequestState
  >
</template>
