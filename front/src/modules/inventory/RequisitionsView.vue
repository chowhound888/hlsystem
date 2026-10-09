<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
import { Plus, RefreshCw } from "lucide-vue-next";
import { ElMessage, ElMessageBox } from "element-plus";
const auth = useAuth(),
  rows = ref<any[]>([]),
  locations = ref<any[]>([]),
  items = ref<any[]>([]),
  open = ref(false),
  detailOpen = ref(false),
  detail = ref<any>(null),
  loading = ref(false),
  form = reactive({ supplyLocationId: "1", items: [] as any[] });
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/inventory/requisitions", {
      locationId: auth.locationId,
    });
  } finally {
    loading.value = false;
  }
}
async function save() {
  await post("/inventory/requisitions", {
    ...form,
    locationId: auth.locationId,
  });
  open.value = false;
  await load();
}
async function show(row: any) {
  detail.value = await get("/inventory/requisitions/" + row.id);
  detailOpen.value = true;
}
async function fulfill(row: any) {
  await ElMessageBox.confirm("根据领药申请生成调拨草稿？", "供应处理");
  const r = await post(
    `/inventory/requisitions/${row.id}/fulfill`,
    {},
    crypto.randomUUID(),
  );
  ElMessage.success("调拨草稿已生成，请在库存单据中审核执行");
  await load();
}
onMounted(async () => {
  await load();
  locations.value = await get("/master/transfer-locations");
  items.value = await get("/master/items");
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>科室与药房领药申请</h1>
      <div>
        <el-button @click="load"><RefreshCw :size="16" />刷新</el-button
        ><el-button
          type="primary"
          @click="
            form.items = [{ itemId: '', quantity: '1' }];
            open = true;
          "
          ><Plus :size="16" />提交申请</el-button
        >
      </div>
    </div>
    <div class="table-region">
      <el-table :data="rows" v-loading="loading"
        ><el-table-column
          prop="number"
          label="申请单号"
          min-width="185"
        /><el-table-column
          prop="requestLocation"
          label="申请机构"
        /><el-table-column
          prop="supplyLocation"
          label="供应机构"
        /><el-table-column prop="creator" label="申请人" /><el-table-column
          label="状态"
          ><template #default="{ row }">{{
            row.status === "FULFILLED" ? "已生成调拨" : "待供货处理"
          }}</template></el-table-column
        ><el-table-column label="操作" width="170"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="show(row)">明细</el-button
            ><el-button
              v-if="
                row.status === 'SUBMITTED' &&
                row.supply_location_id === auth.locationId
              "
              link
              type="primary"
              @click="fulfill(row)"
              >生成调拨</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </div>
    <el-dialog v-model="open" title="领药申请" width="750px"
      ><el-form label-position="top"
        ><el-form-item label="供应机构"
          ><el-select v-model="form.supplyLocationId"
            ><el-option
              v-for="l in locations.filter((l) => l.id !== auth.locationId)"
              :key="l.id"
              :value="l.id"
              :label="l.name" /></el-select></el-form-item></el-form
      ><el-table :data="form.items"
        ><el-table-column label="药品"
          ><template #default="{ row }"
            ><el-select v-model="row.itemId" filterable
              ><el-option
                v-for="i in items"
                :key="i.id"
                :value="i.id"
                :label="
                  i.name + ' · ' + i.unit
                " /></el-select></template></el-table-column
        ><el-table-column label="申请基础数量"
          ><template #default="{ row }"
            ><el-input
              v-model="row.quantity" /></template></el-table-column></el-table
      ><el-button
        style="margin-top: 12px"
        @click="form.items.push({ itemId: '', quantity: '1' })"
        >添加明细</el-button
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" @click="save">提交申请</el-button></template
      ></el-dialog
    ><el-dialog v-model="detailOpen" title="申请明细" width="650px"
      ><el-table :data="detail?.items"
        ><el-table-column prop="name" label="药品" /><el-table-column
          prop="base_qty"
          label="基础数量" /><el-table-column
          prop="unit"
          label="单位" /></el-table
    ></el-dialog>
  </div>
</template>
