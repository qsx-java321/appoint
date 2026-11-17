<script setup lang="ts">
import { onMounted } from "vue";
import { api } from "../api";
import { useRequest } from "../composables/useRequest";
import RequestState from "../components/RequestState.vue";
import type { DoctorAccountList } from "../api/types";

const { data, loading, error, run } = useRequest<DoctorAccountList | null>(
  null,
);
const load = () => run(api.doctorAccounts);
onMounted(load);
</script>

<template>
  <h1>医生账号查询</h1>
  <p class="lead">查看各医院、科室的医生及其当前绑定的登录账号。</p>
  <div class="section-heading">
    <p v-if="data">
      当前环境：{{ data.environment }} · 共 {{ data.items.length }} 位医生
    </p>
    <div class="actions">
      <button type="button" :disabled="loading" @click="load">刷新列表</button>
      <RouterLink to="/admin/doctor-accounts/bind">首次绑定账号</RouterLink>
    </div>
  </div>
  <p class="notice">
    配置文件中的密码用于预置账号首次创建，修改配置不会更新已有账号密码。
  </p>
  <RequestState :loading="loading" :error="error" @retry="load">
    <section v-if="data" class="panel">
      <table v-if="data.items.length" class="data-table">
        <caption>
          医生账号与医院、科室的对应关系
        </caption>
        <thead>
          <tr>
            <th scope="col">医院</th>
            <th scope="col">科室</th>
            <th scope="col">医生 / 编号</th>
            <th scope="col">登录账号 / ID</th>
            <th scope="col">绑定状态</th>
            <th scope="col">密码说明</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in data.items" :key="row.doctorId">
            <td data-label="医院">{{ row.hospitalName }}</td>
            <td data-label="科室">{{ row.departmentName }}</td>
            <td data-label="医生 / 编号">
              {{ row.doctorName }} · {{ row.doctorNo }}
            </td>
            <td data-label="登录账号 / ID">
              <template v-if="row.userId"
                >{{ row.email }}<br />ID：{{ row.userId }}</template
              >
              <template v-else>—</template>
            </td>
            <td data-label="绑定状态">
              {{ row.bindingStatus === "BOUND" ? "已绑定" : "未绑定" }}
            </td>
            <td data-label="密码说明">{{ row.passwordHint }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="feedback">暂无医生档案</p>
    </section>
  </RequestState>
</template>
