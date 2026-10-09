<script setup lang="ts">
import { ref, onMounted } from "vue";
import { get, post } from "../../api/client";
import { ElMessage } from "element-plus";
const rows = ref<any[]>([]),
  saving = ref(false);
const columns: Record<string, string> = {
  name: "药品名称",
  specification: "规格",
  unit: "单位",
  quantity: "数量",
  batch_no: "批号",
  cost_amount: "成本金额",
  directions: "用法",
  amount: "处方金额",
};
onMounted(async () => {
  rows.value = await get("/system/print-templates");
  rows.value.forEach((r) => (r.fields = JSON.parse(r.fields_json)));
});
async function save(row: any) {
  saving.value = true;
  try {
    await post("/system/print-templates/" + row.id, {
      title: row.title,
      orientation: row.paper_orientation,
      fields: row.fields,
    });
    ElMessage.success("模板已保存");
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <div class="page">
    <div class="page-header"><h1>打印模板</h1></div>
    <section v-for="row in rows" :key="row.id" class="section">
      <h2>{{ row.document_type === "INVENTORY" ? "库存单据" : "门诊处方" }}</h2>
      <el-form label-position="top" style="max-width: 750px; margin-top: 20px"
        ><div class="form-grid">
          <el-form-item label="打印标题"
            ><el-input v-model="row.title" /></el-form-item
          ><el-form-item label="纸张方向"
            ><el-radio-group v-model="row.paper_orientation"
              ><el-radio value="portrait">纵向</el-radio
              ><el-radio value="landscape">横向</el-radio></el-radio-group
            ></el-form-item
          >
        </div>
        <el-form-item label="输出列"
          ><el-select v-model="row.fields" multiple style="width: 100%"
            ><el-option
              v-for="(label, value) in columns"
              :key="value"
              :value="value"
              :label="label" /></el-select></el-form-item
        ><el-button type="primary" :loading="saving" @click="save(row)"
          >保存模板</el-button
        ></el-form
      >
    </section>
  </div>
</template>
