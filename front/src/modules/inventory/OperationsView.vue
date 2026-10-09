<script setup lang="ts">
import { ref, onMounted } from "vue";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
import { ElMessageBox } from "element-plus";
import { Plus, RefreshCw, ClipboardCheck } from "lucide-vue-next";
const auth = useAuth(),
  tab = ref("suggestions"),
  suggestions = ref<any[]>([]),
  stocktakes = ref<any[]>([]),
  closings = ref<any[]>([]),
  detail = ref<any>(null),
  open = ref(false),
  loading = ref(false),
  saving = ref(false),
  date = ref(new Date().toLocaleDateString("en-CA")),
  period = ref("DAY");
async function load() {
  loading.value = true;
  try {
    suggestions.value = await get("/inventory/suggestions", {
      locationId: auth.locationId,
    });
    stocktakes.value = await get("/inventory/stocktakes", {
      locationId: auth.locationId,
    });
    closings.value = await get("/inventory/closings", {
      locationId: auth.locationId,
    });
  } finally {
    loading.value = false;
  }
}
async function start() {
  await ElMessageBox.confirm(
    "开始后将冻结该机构的库存操作，直到盘点审核或取消。",
    "开始盘点",
  );
  await post(
    "/inventory/stocktakes",
    { locationId: auth.locationId },
    crypto.randomUUID(),
  );
  await load();
}
async function show(row: any) {
  detail.value = await get("/inventory/stocktakes/" + row.id);
  detail.value.items.forEach((i: any) => {
    i.countedQty = i.counted_qty;
  });
  open.value = true;
}
async function action(a: string) {
  saving.value = true;
  try {
    await post(
      `/inventory/stocktakes/${detail.value.header.id}/${a}`,
      a === "submit"
        ? {
            items: detail.value.items.map((i: any) => ({
              id: i.id,
              countedQty: i.countedQty,
              reason: i.reason ?? "",
            })),
          }
        : {},
      crypto.randomUUID(),
    );
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function close() {
  await ElMessageBox.confirm(
    "确认结账？当前日期结账后，该机构将不能继续库存操作。",
    "期间结账",
  );
  await post(
    "/inventory/closings",
    { locationId: auth.locationId, periodType: period.value, date: date.value },
    crypto.randomUUID(),
  );
  await load();
}
async function reopen(row: any) {
  const r = await ElMessageBox.prompt("请填写重开期间的原因", "重开结账");
  await post(
    `/inventory/closings/${row.id}/reopen`,
    { reason: r.value },
    crypto.randomUUID(),
  );
  await load();
}
onMounted(load);
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>补货建议、盘点与结账</h1>
      <el-button @click="load"><RefreshCw :size="16" />刷新</el-button>
    </div>
    <el-tabs v-model="tab"
      ><el-tab-pane label="补货建议" name="suggestions" /><el-tab-pane
        label="库存盘点"
        name="stocktakes" /><el-tab-pane label="日月年结" name="closings"
    /></el-tabs>
    <div v-if="tab === 'suggestions'" class="table-region">
      <el-table :data="suggestions" v-loading="loading"
        ><el-table-column
          prop="name"
          label="药品"
          min-width="170" /><el-table-column
          prop="specification"
          label="规格" /><el-table-column
          prop="unit"
          label="基础单位" /><el-table-column
          prop="available"
          label="合格可用量"
          align="right" /><el-table-column
          prop="min_qty"
          label="库存低限"
          align="right" /><el-table-column
          prop="target_qty"
          label="目标库存"
          align="right" /><el-table-column
          prop="suggested"
          label="建议补货量"
          align="right"
      /></el-table>
    </div>
    <template v-else-if="tab === 'stocktakes'"
      ><div class="toolbar">
        <el-button
          v-if="auth.can('inventory:write')"
          type="primary"
          @click="start"
          ><Plus :size="16" />开始盘点</el-button
        >
      </div>
      <div class="table-region">
        <el-table :data="stocktakes" v-loading="loading"
          ><el-table-column prop="number" label="盘点单号" /><el-table-column
            label="状态"
            ><template #default="{ row }">{{
              {
                COUNTING: "盘点中",
                SUBMITTED: "待审核",
                EXECUTED: "已执行",
                CANCELLED: "已取消",
              }[row.status as "COUNTING"]
            }}</template></el-table-column
          ><el-table-column
            prop="created_at"
            label="开始时间"
          /><el-table-column label="操作"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="show(row)"
                >详情</el-button
              ></template
            ></el-table-column
          ></el-table
        >
      </div></template
    ><template v-else
      ><div class="toolbar">
        <el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
        /><el-select v-model="period" style="width: 120px"
          ><el-option label="日结" value="DAY" /><el-option
            label="月结"
            value="MONTH" /><el-option label="年结" value="YEAR" /></el-select
        ><el-button
          v-if="auth.can('accounting:write')"
          type="primary"
          @click="close"
          ><ClipboardCheck :size="16" />确认结账</el-button
        >
      </div>
      <div class="table-region">
        <el-table :data="closings"
          ><el-table-column prop="period_type" label="周期" /><el-table-column
            prop="period_start"
            label="开始日期"
          /><el-table-column
            prop="period_end"
            label="结束日期"
          /><el-table-column
            prop="closed_at"
            label="结账时间"
          /><el-table-column prop="status" label="状态" /><el-table-column
            label="操作"
            ><template #default="{ row }"
              ><el-button
                v-if="row.status === 'CLOSED' && auth.can('accounting:write')"
                link
                type="primary"
                @click="reopen(row)"
                >重开</el-button
              ></template
            ></el-table-column
          ></el-table
        >
      </div></template
    ><el-dialog v-model="open" title="库存盘点明细" width="900px"
      ><el-table v-if="detail" :data="detail.items"
        ><el-table-column prop="name" label="药品" /><el-table-column
          prop="batch_no"
          label="批号" /><el-table-column
          prop="unit"
          label="单位"
          width="60" /><el-table-column
          prop="snapshot_qty"
          label="账面数" /><el-table-column label="实盘数"
          ><template #default="{ row }"
            ><el-input
              v-model="row.countedQty"
              :disabled="
                detail.header.status !== 'COUNTING'
              " /></template></el-table-column
        ><el-table-column label="差异原因"
          ><template #default="{ row }"
            ><el-input
              v-model="row.reason"
              :disabled="
                detail.header.status !== 'COUNTING'
              " /></template></el-table-column></el-table
      ><template #footer
        ><template v-if="detail"
          ><el-button
            v-if="
              ['COUNTING', 'SUBMITTED'].includes(detail.header.status) &&
              auth.can('inventory:write')
            "
            @click="action('cancel')"
            >取消盘点</el-button
          ><el-button
            v-if="
              detail.header.status === 'COUNTING' && auth.can('inventory:write')
            "
            type="primary"
            :loading="saving"
            @click="action('submit')"
            >提交实盘</el-button
          ><el-button
            v-if="
              detail.header.status === 'SUBMITTED' &&
              auth.can('inventory:approve')
            "
            type="primary"
            :loading="saving"
            @click="action('execute')"
            >审核并过账</el-button
          ></template
        ></template
      ></el-dialog
    >
  </div>
</template>
