<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
import { ElMessageBox } from "element-plus";
const auth = useAuth(),
  documents = ref<any[]>([]),
  rows = ref<any[]>([]),
  allocations = ref<any[]>([]),
  documentId = ref(""),
  open = ref(false),
  form = reactive({
    allocationId: "",
    type: "LOSS",
    quantity: "1",
    reason: "",
  });
async function load() {
  rows.value = await get("/inventory/transit-adjustments", {
    locationId: auth.locationId,
  });
  documents.value = (
    await get<any[]>("/inventory/documents", { locationId: auth.locationId })
  ).filter((d) => ["SHIPPED", "PARTIALLY_RECEIVED"].includes(d.status));
}
async function select() {
  allocations.value = (
    await get<any[]>(`/inventory/transfers/${documentId.value}/allocations`)
  ).filter((a) => Number(a.remaining_qty) > 0);
}
async function save() {
  await post("/inventory/transit-adjustments", form, crypto.randomUUID());
  open.value = false;
  await load();
}
async function execute(row: any) {
  await ElMessageBox.confirm("确认在途退回或报损？", "独立审核");
  await post(
    `/inventory/transit-adjustments/${row.id}/execute`,
    {},
    crypto.randomUUID(),
  );
  await load();
}
onMounted(load);
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>调拨在途异常处理</h1>
      <el-button type="primary" @click="open = true">申请退回 / 报损</el-button>
    </div>
    <div class="table-region">
      <el-table :data="rows"
        ><el-table-column
          prop="transferNumber"
          label="调拨单号"
          min-width="190"
        /><el-table-column prop="name" label="药品" /><el-table-column
          label="处理类型"
          ><template #default="{ row }">{{
            row.type === "RETURN" ? "退回来源" : "在途报损"
          }}</template></el-table-column
        ><el-table-column prop="quantity" label="数量" /><el-table-column
          prop="reason"
          label="原因"
        /><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button
              v-if="row.status === 'SUBMITTED' && auth.can('inventory:approve')"
              link
              type="primary"
              @click="execute(row)"
              >审核执行</el-button
            ><span v-else>已执行</span></template
          ></el-table-column
        ></el-table
      >
    </div>
    <el-dialog v-model="open" title="在途处理申请" width="650px"
      ><el-form label-position="top"
        ><el-form-item label="调拨单"
          ><el-select v-model="documentId" @change="select" style="width: 100%"
            ><el-option
              v-for="d in documents"
              :key="d.id"
              :value="d.id"
              :label="
                d.number + ' · ' + d.location + ' → ' + d.target
              " /></el-select></el-form-item
        ><el-form-item label="在途批次"
          ><el-select v-model="form.allocationId" style="width: 100%"
            ><el-option
              v-for="a in allocations"
              :key="a.id"
              :value="a.id"
              :label="
                a.name + ' · ' + a.batch_no + ' · 剩余 ' + a.remaining_qty
              " /></el-select></el-form-item
        ><el-form-item label="处理类型"
          ><el-radio-group v-model="form.type"
            ><el-radio value="RETURN">退回来源</el-radio
            ><el-radio value="LOSS">在途报损</el-radio></el-radio-group
          ></el-form-item
        ><el-form-item label="基础数量"
          ><el-input v-model="form.quantity" /></el-form-item
        ><el-form-item label="原因"
          ><el-input
            v-model="form.reason"
            type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button type="primary" @click="save">提交审核</el-button></template
      ></el-dialog
    >
  </div>
</template>
