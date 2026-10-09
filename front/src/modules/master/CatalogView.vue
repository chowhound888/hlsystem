<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { useRoute } from "vue-router";
import { Plus } from "lucide-vue-next";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
const type = String(useRoute().params.type),
  auth = useAuth(),
  rows = ref<any[]>([]),
  loading = ref(false),
  open = ref(false),
  saving = ref(false),
  form = reactive({ code: "", name: "" });
const names: Record<string, string> = {
  suppliers: "供应商",
  manufacturers: "生产厂家",
  units: "计量单位",
  diagnoses: "诊断字典",
};
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/master/catalogs/" + type);
  } finally {
    loading.value = false;
  }
}
async function save() {
  if (!form.code.trim() || !form.name.trim()) return;
  saving.value = true;
  try {
    await post("/master/catalogs/" + type, form);
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ names[type] ?? "基础资料" }}</h1>
      <el-button
        v-if="auth.can('master:write')"
        type="primary"
        @click="
          form.code = '';
          form.name = '';
          open = true;
        "
        ><Plus :size="16" /> 新增</el-button
      >
    </div>
    <div class="table-region">
      <el-table :data="rows" v-loading="loading"
        ><el-table-column
          prop="code"
          label="编码"
          width="180" /><el-table-column
          prop="name"
          label="名称"
          min-width="220" /><el-table-column
          v-if="type === 'suppliers' || type === 'manufacturers'"
          prop="contact"
          label="联系信息" /><el-table-column
          v-if="type === 'units'"
          prop="dimension"
          label="计量类别"
      /></el-table>
    </div>
    <el-dialog v-model="open" :title="'新增' + names[type]" width="440px"
      ><el-form label-position="top" @submit.prevent="save"
        ><el-form-item label="编码" required
          ><el-input v-model="form.code" maxlength="64" /></el-form-item
        ><el-form-item label="名称" required
          ><el-input
            v-model="form.name"
            maxlength="200" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
