<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
import { ElMessageBox } from "element-plus";
import { Plus } from "lucide-vue-next";
const auth = useAuth(),
  open = ref(false),
  roles = ref<any[]>([]),
  departments = ref<any[]>([]),
  locations = ref<any[]>([]),
  saving = ref(false),
  form = reactive({
    username: "",
    displayName: "",
    password: "",
    departmentId: "1",
    roleId: "2",
    locationIds: [] as string[],
    code: "",
    name: "",
    type: "WAREHOUSE",
  });
const route = useRoute(),
  rows = ref<any[]>([]),
  loading = ref(false),
  type = String(route.params.type);
const title = computed(() =>
  type === "users"
    ? "用户与角色"
    : type === "locations"
      ? "组织与库存机构"
      : "操作日志",
);
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/system/" + type);
  } finally {
    loading.value = false;
  }
}
async function save() {
  saving.value = true;
  try {
    await post("/system/" + type, form);
    open.value = false;
    await load();
    await auth.load();
  } finally {
    saving.value = false;
  }
}
async function toggle(row: any) {
  await ElMessageBox.confirm("确认更改账号启用状态？", "账号管理");
  await post("/system/users/" + row.id + "/enabled", { enabled: !row.enabled });
  await load();
}
async function reset(row: any) {
  const r = await ElMessageBox.prompt("请输入至少6位的新密码", "重置账号密码", {
    inputType: "password",
    inputValidator: (v) => v.length >= 6 || "密码至少6位",
  });
  await post("/system/users/" + row.id + "/reset-password", {
    password: r.value,
  });
}
onMounted(async () => {
  await load();
  if (auth.can("system:write")) {
    roles.value = await get("/system/roles");
    departments.value = await get("/system/departments");
    locations.value = await get("/system/locations");
  }
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ title }}</h1>
      <span class="muted">{{ rows.length }} 条记录</span>
      <el-button
        v-if="type !== 'audit-logs' && auth.can('system:write')"
        type="primary"
        @click="open = true"
        ><Plus :size="16" />新增</el-button
      >
    </div>
    <div class="table-region">
      <el-table :data="rows" v-loading="loading"
        ><template v-if="type === 'users'"
          ><el-table-column prop="username" label="用户名" /><el-table-column
            prop="displayName"
            label="姓名"
          /><el-table-column prop="department" label="科室" /><el-table-column
            prop="roles"
            label="角色"
          /><el-table-column label="状态"
            ><template #default="{ row }"
              ><el-tag :type="row.enabled ? 'success' : 'info'">{{
                row.enabled ? "启用" : "停用"
              }}</el-tag></template
            ></el-table-column
          ><el-table-column label="操作" width="180"
            ><template #default="{ row }"
              ><el-button
                v-if="row.id !== auth.user?.id"
                link
                type="primary"
                @click="toggle(row)"
                >{{ row.enabled ? "停用" : "启用" }}</el-button
              ><el-button link type="primary" @click="reset(row)"
                >重置密码</el-button
              ></template
            ></el-table-column
          ></template
        ><template v-else-if="type === 'locations'"
          ><el-table-column prop="code" label="编码" /><el-table-column
            prop="name"
            label="机构名称" /><el-table-column
            prop="department"
            label="所属科室" /><el-table-column
            prop="type"
            label="类型" /></template
        ><template v-else
          ><el-table-column
            prop="occurred_at"
            label="时间"
            min-width="180" /><el-table-column
            prop="operator"
            label="操作员" /><el-table-column
            prop="action"
            label="动作" /><el-table-column
            prop="source_type"
            label="对象" /><el-table-column
            prop="reason"
            label="原因"
            min-width="200" /></template
      ></el-table>
    </div>
    <el-dialog
      v-model="open"
      :title="type === 'users' ? '新增用户' : '新增库存机构'"
      width="620px"
      ><el-form label-position="top"
        ><div class="form-grid">
          <template v-if="type === 'users'"
            ><el-form-item label="用户名"
              ><el-input v-model="form.username" /></el-form-item
            ><el-form-item label="姓名"
              ><el-input v-model="form.displayName" /></el-form-item
            ><el-form-item label="初始密码"
              ><el-input
                v-model="form.password"
                type="password"
                show-password /></el-form-item
            ><el-form-item label="角色"
              ><el-select v-model="form.roleId"
                ><el-option
                  v-for="r in roles"
                  :key="r.id"
                  :value="r.id"
                  :label="r.name" /></el-select></el-form-item></template
          ><template v-else
            ><el-form-item label="机构编码"
              ><el-input v-model="form.code" /></el-form-item
            ><el-form-item label="机构名称"
              ><el-input v-model="form.name" /></el-form-item
            ><el-form-item label="类型"
              ><el-select v-model="form.type"
                ><el-option
                  v-for="(label, value) in {
                    WAREHOUSE: '药库',
                    OUTPATIENT: '门诊药房',
                    INPATIENT: '住院药房',
                    WARD: '病区',
                    PREPARATION: '制剂库',
                  }"
                  :key="value"
                  :value="value"
                  :label="label" /></el-select></el-form-item></template
          ><el-form-item label="所属科室"
            ><el-select v-model="form.departmentId"
              ><el-option
                v-for="d in departments"
                :key="d.id"
                :value="d.id"
                :label="d.name" /></el-select
          ></el-form-item>
        </div>
        <el-form-item v-if="type === 'users'" label="授权机构"
          ><el-select v-model="form.locationIds" multiple style="width: 100%"
            ><el-option
              v-for="l in locations"
              :key="l.id"
              :value="l.id"
              :label="l.name" /></el-select></el-form-item></el-form
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
