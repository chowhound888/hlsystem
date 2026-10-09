<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
import { Plus } from "lucide-vue-next";
const auth = useAuth(),
  rows = ref<any[]>([]),
  units = ref<any[]>([]),
  open = ref(false),
  form = reactive({
    code: "",
    name: "",
    specification: "",
    itemType: "RAW_MATERIAL",
    baseUnitId: "5",
    quantityScale: 3,
  });
const types: Record<string, string> = {
  RAW_MATERIAL: "原料",
  AUXILIARY: "辅料",
  PACKAGING: "包装材料",
  SEMI_FINISHED: "半成品",
};
async function load() {
  rows.value = (await get<any[]>("/master/items")).filter(
    (i) => i.item_type !== "DRUG",
  );
}
async function save() {
  await post("/master/items", form);
  open.value = false;
  await load();
}
onMounted(async () => {
  await load();
  units.value = (await get("/master/options")).units;
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>制剂物料字典</h1>
      <el-button
        v-if="auth.can('master:write')"
        type="primary"
        @click="open = true"
        ><Plus :size="16" />新增物料</el-button
      >
    </div>
    <div class="table-region">
      <el-table :data="rows"
        ><el-table-column prop="code" label="编码" /><el-table-column
          prop="name"
          label="名称" /><el-table-column
          prop="specification"
          label="规格" /><el-table-column label="类别"
          ><template #default="{ row }">{{
            types[row.item_type]
          }}</template></el-table-column
        ><el-table-column prop="unit" label="基础单位" /><el-table-column
          prop="quantity_scale"
          label="小数精度"
      /></el-table>
    </div>
    <el-dialog v-model="open" title="新增物料" width="550px"
      ><el-form label-position="top"
        ><div class="form-grid">
          <el-form-item label="编码"
            ><el-input v-model="form.code" /></el-form-item
          ><el-form-item label="名称"
            ><el-input v-model="form.name" /></el-form-item
          ><el-form-item label="规格"
            ><el-input v-model="form.specification" /></el-form-item
          ><el-form-item label="类别"
            ><el-select v-model="form.itemType"
              ><el-option
                v-for="(label, value) in types"
                :key="value"
                :value="value"
                :label="label" /></el-select></el-form-item
          ><el-form-item label="基础单位"
            ><el-select v-model="form.baseUnitId"
              ><el-option
                v-for="u in units"
                :key="u.id"
                :value="u.id"
                :label="u.name" /></el-select></el-form-item
          ><el-form-item label="数量小数位"
            ><el-input-number v-model="form.quantityScale" :min="0" :max="6"
          /></el-form-item></div></el-form
      ><template #footer
        ><el-button type="primary" @click="save">保存</el-button></template
      ></el-dialog
    >
  </div>
</template>
