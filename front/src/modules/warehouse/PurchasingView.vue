<script setup lang="ts">
import { ref, onMounted } from "vue";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
import { Plus, RefreshCw } from "lucide-vue-next";
import { ElMessageBox } from "element-plus";
const auth = useAuth(),
  rows = ref<any[]>([]),
  suggestions = ref<any[]>([]),
  options = ref<any>({ suppliers: [] }),
  open = ref(false),
  loading = ref(false),
  selected = ref<any[]>([]),
  supplierId = ref(""),
  detail = ref<any>(null),
  detailOpen = ref(false);
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/purchasing/plans", {
      locationId: auth.locationId,
    });
    suggestions.value = await get("/inventory/suggestions", {
      locationId: auth.locationId,
    });
    suggestions.value.forEach(
      (r) => (r.quantity = Number(r.suggested) > 0 ? r.suggested : "1"),
    );
  } finally {
    loading.value = false;
  }
}
async function save() {
  await post("/purchasing/plans", {
    locationId: auth.locationId,
    supplierId: supplierId.value,
    items: selected.value.map((r) => ({
      itemId: r.itemId,
      quantity: r.quantity,
    })),
  });
  open.value = false;
  await load();
}
async function approve(row: any) {
  await ElMessageBox.confirm("确认采购计划审核通过？", "采购审核");
  await post(`/purchasing/plans/${row.id}/approve`, {}, crypto.randomUUID());
  await load();
}
async function show(row: any) {
  detail.value = await get("/purchasing/plans/" + row.id);
  detailOpen.value = true;
}
async function receive(row: any) {
  const batch = await ElMessageBox.prompt(
    "请输入本次收货批号前缀",
    "生成采购入库草稿",
  );
  const cost = await ElMessageBox.prompt(
    "请输入基础单位购进价（每个品种的实际单价可在后续独立入库单填写）",
    "采购价格",
    { inputPattern: /^\d+(\.\d{1,6})?$/, inputErrorMessage: "请输入有效价格" },
  );
  const expiry = await ElMessageBox.prompt(
    "请输入有效期 YYYY-MM-DD",
    "有效期",
    {
      inputPattern: /^\d{4}-\d{2}-\d{2}$/,
      inputErrorMessage: "日期格式不正确",
    },
  );
  await post(
    `/purchasing/plans/${row.id}/receipt`,
    {
      batchNo: batch.value,
      manufacturedOn: new Date().toLocaleDateString("en-CA"),
      expiresOn: expiry.value,
      unitCost: cost.value,
    },
    crypto.randomUUID(),
  );
  await load();
}
onMounted(async () => {
  await load();
  options.value = await get("/master/options");
  supplierId.value = options.value.suppliers[0]?.id ?? "";
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>采购计划与采购单</h1>
      <div>
        <el-button @click="load"><RefreshCw :size="16" />刷新</el-button
        ><el-button
          v-if="auth.can('inventory:write')"
          type="primary"
          @click="open = true"
          ><Plus :size="16" />按建议创建采购计划</el-button
        >
      </div>
    </div>
    <div class="table-region">
      <el-table :data="rows" v-loading="loading"
        ><el-table-column
          prop="number"
          label="采购单号"
          min-width="190"
        /><el-table-column
          prop="supplierName"
          label="供应商"
          min-width="170"
        /><el-table-column label="状态"
          ><template #default="{ row }">{{
            {
              SUBMITTED: "待审核",
              APPROVED: "已审核待到货",
              ORDERED: "已关联入库",
            }[row.status as "SUBMITTED"]
          }}</template></el-table-column
        ><el-table-column
          prop="created_at"
          label="创建时间"
          width="190"
        /><el-table-column label="操作" width="230"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="show(row)">明细</el-button
            ><el-button
              v-if="row.status === 'SUBMITTED' && auth.can('inventory:approve')"
              link
              type="primary"
              @click="approve(row)"
              >审核</el-button
            ><el-button
              v-if="row.status === 'APPROVED' && auth.can('inventory:write')"
              link
              type="primary"
              @click="receive(row)"
              >生成入库单</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </div>
    <el-dialog v-model="open" title="采购建议转计划" width="860px"
      ><el-form label-position="top"
        ><el-form-item label="供应商"
          ><el-select v-model="supplierId"
            ><el-option
              v-for="s in options.suppliers"
              :key="s.id"
              :value="s.id"
              :label="s.name" /></el-select></el-form-item></el-form
      ><el-table :data="suggestions" @selection-change="selected = $event"
        ><el-table-column type="selection" width="50" /><el-table-column
          prop="name"
          label="药品" /><el-table-column
          prop="unit"
          label="单位"
          width="70" /><el-table-column
          prop="available"
          label="可用库存" /><el-table-column
          prop="suggested"
          label="建议数量" /><el-table-column label="采购基础数量"
          ><template #default="{ row }"
            ><el-input
              v-model="row.quantity" /></template></el-table-column></el-table
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :disabled="!selected.length" @click="save"
          >提交计划</el-button
        ></template
      ></el-dialog
    ><el-dialog v-model="detailOpen" title="采购明细" width="750px"
      ><el-table :data="detail?.items"
        ><el-table-column prop="name" label="药品" /><el-table-column
          prop="specification"
          label="规格" /><el-table-column
          prop="base_qty"
          label="采购基础数量" /><el-table-column
          prop="unit"
          label="单位" /></el-table
    ></el-dialog>
  </div>
</template>
