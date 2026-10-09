<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { Plus, CalendarClock, Printer, RefreshCw } from "lucide-vue-next";
import { ElMessageBox } from "element-plus";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
const window = globalThis.window,
  auth = useAuth(),
  orders = ref<any[]>([]),
  executions = ref<any[]>([]),
  drugs = ref<any[]>([]),
  encounters = ref<any[]>([]),
  loading = ref(false),
  open = ref(false),
  tab = ref("orders"),
  date = ref(new Date().toLocaleDateString("en-CA")),
  saving = ref(false);
const form = reactive({
    encounterId: "",
    itemId: "",
    orderType: "LONG_TERM",
    startDate: date.value,
    endDate: "",
    intervalDays: 1,
    times: ["08:00", "20:00"],
    quantity: "1",
    dose: "250",
    doseUnit: "mg",
    route: "口服",
  }),
  timesText = ref("08:00,20:00");
const statuses: Record<string, string> = {
  PENDING: "待确认",
  CONFIRMED: "已记账",
  PICKED: "已摆药",
  DISPENSED: "已发药",
  RETURNED: "已退药",
  CANCELLED: "已取消",
  ACTIVE: "执行中",
  STOPPED: "已停止",
};
async function load() {
  loading.value = true;
  try {
    orders.value = await get("/inpatient/orders", {
      locationId: auth.locationId,
    });
    if (auth.can("inpatient:read"))
      executions.value = await get("/inpatient/executions", {
        locationId: auth.locationId,
        date: date.value,
      });
  } finally {
    loading.value = false;
  }
}
async function newOrder() {
  encounters.value = await get("/clinical/encounters", { type: "INPATIENT" });
  form.encounterId = encounters.value[0]?.id ?? "";
  open.value = true;
}
async function save() {
  saving.value = true;
  try {
    await post("/inpatient/orders", {
      ...form,
      locationId: auth.locationId,
      times: timesText.value
        .split(/[,，]/)
        .map((t) => t.trim())
        .filter(Boolean),
    });
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function generate() {
  await post(
    "/inpatient/executions/generate",
    { locationId: auth.locationId, date: date.value },
    crypto.randomUUID(),
  );
  tab.value = "executions";
  await load();
}
async function action(row: any, a: string) {
  let reason = "";
  if (a === "confirm" || a === "dispense") {
    const r = await ElMessageBox.prompt(
      a === "dispense"
        ? "控制药品须填写领药人及用途，普通药品可留空"
        : "有普通用药警告或欠费放行时填写理由",
      "确认医嘱记账",
    );
    reason = r.value ?? "";
  } else await ElMessageBox.confirm("确认执行此操作？", "医嘱执行");
  await post(
    `/inpatient/executions/${row.id}/${a}`,
    { reason },
    crypto.randomUUID(),
  );
  await load();
}
async function stop(row: any) {
  await ElMessageBox.confirm(
    "停止后，未发药执行实例将取消并冲回费用。",
    "停止医嘱",
  );
  await post(`/inpatient/orders/${row.id}/stop`, {}, crypto.randomUUID());
  await load();
}
async function returnDrugs(row: any) {
  const r = await ElMessageBox.prompt(
    "本次执行全部退回，药品进入隔离库存，费用退回押金账户。请填写原因。",
    "住院退药",
  );
  await post(
    `/inpatient/executions/${row.id}/returns`,
    { reason: r.value },
    crypto.randomUUID(),
  );
  await load();
}
onMounted(async () => {
  await load();
  drugs.value = (await get("/master/drugs", { pageSize: 100 })).records;
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>住院医嘱与摆药</h1>
      <div>
        <el-button @click="load"><RefreshCw :size="16" /> 刷新</el-button
        ><el-button
          v-if="auth.can('clinical:write')"
          type="primary"
          @click="newOrder"
          ><Plus :size="16" /> 新增医嘱</el-button
        >
      </div>
    </div>
    <div class="filters">
      <el-date-picker
        v-model="date"
        value-format="YYYY-MM-DD"
        type="date"
        @change="load"
      /><el-button
        v-if="auth.can('inpatient:write')"
        type="primary"
        @click="generate"
        ><CalendarClock :size="16" /> 生成当日执行</el-button
      ><el-button @click="window.print()"
        ><Printer :size="16" /> 打印摆药单</el-button
      >
    </div>
    <el-tabs v-model="tab" style="margin-top: 16px"
      ><el-tab-pane label="医嘱列表" name="orders" /><el-tab-pane
        v-if="auth.can('inpatient:read')"
        label="执行与摆药"
        name="executions"
    /></el-tabs>
    <div class="table-region">
      <el-table
        :data="tab === 'orders' ? orders : executions"
        v-loading="loading"
        ><el-table-column
          prop="patientName"
          label="患者"
          width="110"
        /><el-table-column
          prop="bed_no"
          label="床号"
          width="70"
        /><el-table-column
          prop="drugName"
          label="药品"
          min-width="170"
        /><el-table-column label="医嘱类型" width="100"
          ><template #default="{ row }">{{
            { LONG_TERM: "长期", TEMPORARY: "临时", HERBAL: "中草药" }[
              row.order_type as "LONG_TERM"
            ]
          }}</template></el-table-column
        ><el-table-column
          prop="base_qty"
          label="每次基础数量"
          width="120"
        /><el-table-column label="剂量 / 途径" min-width="150"
          ><template #default="{ row }"
            >{{ row.dose }}{{ row.dose_unit }} · {{ row.route }}</template
          ></el-table-column
        ><template v-if="tab === 'orders'"
          ><el-table-column
            prop="start_date"
            label="开始日期"
            width="115"
          /><el-table-column label="间隔" width="90"
            ><template #default="{ row }"
              >每{{ row.interval_days }}日</template
            ></el-table-column
          ></template
        ><template v-else
          ><el-table-column
            prop="scheduled_at"
            label="执行时间"
            width="175" /><el-table-column
            prop="amount"
            label="费用（元）"
            width="100"
            align="right" /></template
        ><el-table-column label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag>{{ statuses[row.status] }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="170" fixed="right"
          ><template #default="{ row }"
            ><template v-if="tab === 'orders'"
              ><el-button
                v-if="row.status === 'ACTIVE' && auth.can('clinical:write')"
                link
                type="danger"
                @click="stop(row)"
                >停止</el-button
              ></template
            ><template v-else-if="auth.can('inpatient:write')"
              ><el-button
                v-if="row.status === 'PENDING'"
                link
                type="primary"
                @click="action(row, 'confirm')"
                >确认记账</el-button
              ><el-button
                v-if="row.status === 'CONFIRMED'"
                link
                type="primary"
                @click="action(row, 'pick')"
                >摆药复核</el-button
              ><el-button
                v-if="row.status === 'PICKED'"
                link
                type="primary"
                @click="action(row, 'dispense')"
                >发药</el-button
              ><el-button
                v-if="row.status === 'DISPENSED'"
                link
                type="primary"
                @click="returnDrugs(row)"
                >退药</el-button
              ><el-button
                v-if="['PENDING', 'CONFIRMED', 'PICKED'].includes(row.status)"
                link
                type="danger"
                @click="action(row, 'cancel')"
                >取消</el-button
              ></template
            ></template
          ></el-table-column
        ></el-table
      >
    </div>
    <div class="print-only signature">
      <span>制单人：________</span><span>复核人：________</span
      ><span>发药人：________</span>
    </div>
    <el-dialog v-model="open" title="录入住院医嘱" width="700px"
      ><el-form label-position="top"
        ><div class="form-grid">
          <el-form-item label="住院患者" required
            ><el-select v-model="form.encounterId" filterable
              ><el-option
                v-for="e in encounters"
                :key="e.id"
                :value="e.id"
                :label="
                  e.patientName + ' · ' + e.bed_no
                " /></el-select></el-form-item
          ><el-form-item label="药品" required
            ><el-select v-model="form.itemId" filterable
              ><el-option
                v-for="d in drugs"
                :key="d.id"
                :value="d.id"
                :label="
                  d.name + ' ' + d.specification
                " /></el-select></el-form-item
          ><el-form-item label="医嘱类型"
            ><el-select v-model="form.orderType"
              ><el-option label="长期" value="LONG_TERM" /><el-option
                label="临时"
                value="TEMPORARY" /><el-option
                label="中草药"
                value="HERBAL" /></el-select></el-form-item
          ><el-form-item label="每次基础单位数量"
            ><el-input v-model="form.quantity" /></el-form-item
          ><el-form-item label="开始日期"
            ><el-date-picker
              v-model="form.startDate"
              type="date"
              value-format="YYYY-MM-DD" /></el-form-item
          ><el-form-item label="结束日期"
            ><el-date-picker
              v-model="form.endDate"
              type="date"
              value-format="YYYY-MM-DD" /></el-form-item
          ><el-form-item label="间隔天数"
            ><el-input-number
              v-model="form.intervalDays"
              :min="1"
              :max="30" /></el-form-item
          ><el-form-item label="执行时段"
            ><el-input
              v-model="timesText"
              placeholder="08:00,20:00" /></el-form-item
          ><el-form-item label="单次剂量"
            ><el-input v-model="form.dose" /></el-form-item
          ><el-form-item label="剂量单位"
            ><el-select v-model="form.doseUnit"
              ><el-option
                v-for="u in ['mg', 'g', 'ml', '片', '支']"
                :key="u"
                :value="u"
                :label="u" /></el-select></el-form-item
          ><el-form-item label="给药途径"
            ><el-select v-model="form.route"
              ><el-option
                v-for="r in ['口服', '静滴', '肌注', '外用', '煎服']"
                :key="r"
                :value="r"
                :label="r" /></el-select
          ></el-form-item></div></el-form
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存医嘱</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
