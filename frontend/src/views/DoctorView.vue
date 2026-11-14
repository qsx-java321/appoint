<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { api } from "../api";
import type { DoctorDetail, DoctorRow, DoctorSession } from "../api/types";
import { message } from "../api/client";
import { useRequest } from "../composables/useRequest";
import RequestState from "../components/RequestState.vue";
import { sessionNames, time, today, visitNames } from "../lib/presentation";
const date = ref(today()),
  session = ref("MORNING_FIRST"),
  detail = ref<DoctorDetail | null>(null),
  detailTarget = ref(""),
  detailLoading = ref(false),
  actionError = ref(""),
  notice = ref(""),
  busy = ref(new Set<string>());
const { data, loading, error, run } = useRequest<{
  sessions: DoctorSession[];
  rows: DoctorRow[];
  context: string;
} | null>(null);
function load() {
  const day = date.value,
    type = session.value;
  return run(async () => {
    const [sessions, rows] = await Promise.all([
      api.sessions(day),
      api.doctorRows(day, type),
    ]);
    return { sessions, rows, context: day + type };
  });
}
watch(
  [date, session],
  () => {
    detail.value = null;
    detailTarget.value = "";
    void load();
  },
  { immediate: true },
);
async function view(row: DoctorRow) {
  detailTarget.value = row.appointmentId;
  detailLoading.value = true;
  actionError.value = "";
  try {
    const result = await api.doctorDetail(row.appointmentId);
    if (detailTarget.value === row.appointmentId) {
      detail.value = result;
      await nextTick();
      document
        .querySelector<HTMLButtonElement>("#close-doctor-detail")
        ?.focus();
    }
  } catch (e) {
    if (detailTarget.value === row.appointmentId)
      actionError.value = message(e);
  } finally {
    if (detailTarget.value === row.appointmentId) detailLoading.value = false;
  }
}
async function close() {
  const target = detailTarget.value;
  detail.value = null;
  detailTarget.value = "";
  await nextTick();
  document.getElementById(`view-${target}`)?.focus();
}
async function act(row: DoctorRow, action: "start-visit" | "complete-visit") {
  if (busy.value.has(row.appointmentId)) return;
  busy.value.add(row.appointmentId);
  actionError.value = "";
  notice.value = "";
  try {
    await api.visitAction(row.appointmentId, action);
    notice.value = `${row.patientName}：${action === "start-visit" ? "已开始接诊" : "已完成接诊"}`;
    await load();
  } catch (e) {
    actionError.value = message(e);
    await load();
  } finally {
    busy.value.delete(row.appointmentId);
  }
}
</script>
<template>
  <div class="section-heading">
    <div>
      <h1>当日接诊</h1>
      <p class="lead">按本场就诊序号查看与接诊</p>
    </div>
    <div class="actions">
      <label>日期<input v-model="date" type="date" required /></label
      ><button :disabled="loading" @click="load">刷新名单</button>
    </div>
  </div>
  <p v-if="actionError" class="notice error" role="alert">{{ actionError }}</p>
  <p v-if="notice" class="notice" role="status">{{ notice }}</p>
  <RequestState
    :loading="loading && (!data || data.context !== date + session)"
    :error="error"
    @retry="load"
    ><div v-if="data" class="doctor-layout">
      <div class="session-grid">
        <button
          v-for="s in data.sessions"
          :key="s.sessionType"
          class="session-card"
          :aria-pressed="session === s.sessionType"
          @click="session = s.sessionType"
        >
          <strong>{{ sessionNames[s.sessionType] }}</strong><span
            >{{ s.startTime.slice(0, 5) }}–{{ s.endTime.slice(0, 5) }}</span
          ><span class="session-total"
            >{{ s.confirmedCount }} <small>位预约</small></span
          ><span class="session-counts"
            ><span>待签到 {{ s.notCheckedInCount }}</span
            ><span>候诊 {{ s.waitingCount }}</span
            ><span>就诊中 {{ s.inProgressCount }}</span
            ><span>已就诊 {{ s.completedCount }}</span></span
          >
        </button>
      </div>
      <section class="panel">
        <h2>{{ sessionNames[session] }} · 人员名单</h2>
        <table v-if="data.rows.length" class="data-table">
          <thead>
            <tr>
              <th>序号</th>
              <th>就诊人</th>
              <th>状态</th>
              <th>签到时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in data.rows" :key="row.appointmentId">
              <td data-label="序号">
                <strong>{{ String(row.visitNumber).padStart(3, "0") }}</strong>
              </td>
              <td data-label="就诊人">{{ row.patientName }}</td>
              <td data-label="状态">{{ visitNames[row.visitStatus] }}</td>
              <td data-label="签到时间">{{ time(row.checkedInAt) }}</td>
              <td data-label="操作">
                <div class="actions">
                  <button :id="`view-${row.appointmentId}`" @click="view(row)">
                    查看</button
                  ><button
                    :disabled="
                      !row.canStartVisit || busy.has(row.appointmentId)
                    "
                    :class="{ primary: row.canStartVisit }"
                    @click="act(row, 'start-visit')"
                  >
                    开始接诊</button
                  ><button
                    :disabled="
                      !row.canCompleteVisit || busy.has(row.appointmentId)
                    "
                    :class="{ primary: row.canCompleteVisit }"
                    @click="act(row, 'complete-visit')"
                  >
                    完成接诊
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="feedback">本场暂无预约</p>
        <p class="muted">
          号码顺序固定。未签到者不阻塞已签到者；详情只读，接诊操作在名单中完成。
        </p>
      </section>
      <p v-if="detailLoading" role="status">正在读取就诊详情…</p>
      <aside v-if="detail" class="panel doctor-detail">
        <div class="section-heading">
          <h2>{{ detail.patient.name }} · 就诊详情</h2>
          <button id="close-doctor-detail" @click="close">关闭详情</button>
        </div>
        <dl class="facts">
          <dt>预约单号</dt>
          <dd>{{ detail.appointmentNo }}</dd>
          <dt>就诊序号</dt>
          <dd>{{ String(detail.visitNumber).padStart(3, "0") }}</dd>
          <dt>身份证</dt>
          <dd>{{ detail.patient.documentNoMasked }}</dd>
          <dt>日期场次</dt>
          <dd>
            {{ detail.appointmentDate }} {{ detail.startTime.slice(0, 5) }}–{{
              detail.endTime.slice(0, 5)
            }}
          </dd>
          <dt>医院医生</dt>
          <dd>
            {{ detail.doctor.hospital.name }} ·
            {{ detail.doctor.department.name }} · {{ detail.doctor.name }}
          </dd>
          <dt>当前状态</dt>
          <dd>{{ visitNames[detail.visit.status] }}</dd>
          <dt>签到时间</dt>
          <dd>{{ time(detail.visit.checkedInAt) }}</dd>
          <dt>开始接诊</dt>
          <dd>{{ time(detail.visit.startedAt) }}</dd>
          <dt>完成接诊</dt>
          <dd>{{ time(detail.visit.completedAt) }}</dd>
        </dl>
      </aside>
    </div></RequestState
  >
</template>
