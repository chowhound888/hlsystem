<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { Plus, RefreshCw, ShieldCheck, Printer } from "lucide-vue-next";
import { ElMessage, ElMessageBox } from "element-plus";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
const window = globalThis.window;
const auth = useAuth(),
  rows = ref<any[]>([]),
  drugs = ref<any[]>([]),
  encounters = ref<any[]>([]),
  loading = ref(false),
  saving = ref(false),
  open = ref(false),
  detailOpen = ref(false),
  detail = ref<any>(null),
  checkResult = ref<any>(null),
  filter = ref("ALL");
const statuses: Record<string, string> = {
  DRAFT: "草稿",
  SUBMITTED: "待审查划价",
  PRICED: "待缴费",
  PAID: "待复核",
  VERIFIED: "待发药",
  DISPENSED: "已发药",
  CANCELLED: "已取消",
};
const form = reactive({ encounterId: "", items: [] as any[] });
const visible = computed(() =>
  filter.value === "ALL"
    ? rows.value
    : rows.value.filter((r) => r.status === filter.value),
);
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/outpatient/prescriptions", {
      locationId: auth.locationId,
    });
  } finally {
    loading.value = false;
  }
}
async function newRx() {
  encounters.value = await get("/clinical/encounters", { type: "OUTPATIENT" });
  form.encounterId = encounters.value[0]?.id ?? "";
  form.items = [];
  addLine();
  open.value = true;
}
function addLine() {
  form.items.push({
    itemId: "",
    unitId: "",
    quantity: "1",
    dose: "250",
    doseUnit: "mg",
    frequencyPerDay: 3,
    days: 3,
    route: "口服",
    units: [],
  });
}
async function selectDrug(line: any) {
  const d = await get("/master/drugs/" + line.itemId);
  line.units = d.units;
  line.unitId = d.units[0]?.unitId ?? "";
}
async function save() {
  if (!form.encounterId || form.items.some((i) => !i.itemId)) {
    ElMessage.warning("请选择就诊和药品");
    return;
  }
  saving.value = true;
  try {
    await post("/outpatient/prescriptions", {
      ...form,
      locationId: auth.locationId,
      items: form.items.map(({ units, ...i }) => i),
    });
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function show(row: any) {
  detail.value = await get("/outpatient/prescriptions/" + row.id);
  checkResult.value = null;
  detailOpen.value = true;
}
async function check() {
  checkResult.value = await post(
    `/outpatient/prescriptions/${detail.value.id}/check`,
  );
}
async function action(a: string) {
  let reason = "";
  if (["price", "verify", "dispense"].includes(a)) {
    const r = await ElMessageBox.prompt(
      a === "dispense"
        ? "填写领药人（控制药须同时注明用途）"
        : "有普通用药警告时请填写处理理由",
      "确认" +
        { price: "划价", verify: "复核", dispense: "发药" }[a as "price"],
      { inputValue: "", inputPlaceholder: "无警告且无控制药时可留空" },
    );
    reason = r.value ?? "";
  } else await ElMessageBox.confirm("确认执行此操作？", "处方操作");
  saving.value = true;
  try {
    await post(
      `/outpatient/prescriptions/${detail.value.id}/${a}`,
      { version: detail.value.version, reason },
      crypto.randomUUID(),
    );
    await show(detail.value);
    await load();
  } finally {
    saving.value = false;
  }
}
async function returnDrug(line: any) {
  const qty = await ElMessageBox.prompt(
    "请输入退药数量（基础单位），剩余可退 " +
      (Number(line.base_qty) - Number(line.returned_base_qty)),
    "退药",
    { inputPattern: /^\d+(\.\d{1,6})?$/, inputErrorMessage: "请输入有效数量" },
  );
  const reason = await ElMessageBox.prompt("请输入退药原因", "退药");
  await post(
    `/outpatient/prescriptions/${detail.value.id}/returns`,
    {
      itemId: line.id,
      quantity: qty.value,
      reason: reason.value,
      qualityDisposition: "QUARANTINE",
    },
    crypto.randomUUID(),
  );
  await show(detail.value);
}
async function refund(row: any) {
  await ElMessageBox.confirm("确认退款 ¥" + row.amount + "？", "退药退款");
  await post(`/outpatient/returns/${row.id}/refund`, {}, crypto.randomUUID());
  await show(detail.value);
}
onMounted(async () => {
  await load();
  drugs.value = (await get("/master/drugs", { pageSize: 100 })).records;
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>门诊处方与发药</h1>
        <p class="muted">
          {{ auth.user?.locations.find((l) => l.id === auth.locationId)?.name }}
        </p>
      </div>
      <div>
        <el-button @click="load"><RefreshCw :size="16" /> 刷新</el-button
        ><el-button
          v-if="auth.can('clinical:write')"
          type="primary"
          @click="newRx"
          ><Plus :size="16" /> 新增处方</el-button
        >
      </div>
    </div>
    <el-tabs v-model="filter"
      ><el-tab-pane label="全部" name="ALL" /><el-tab-pane
        v-for="(label, state) in statuses"
        :key="state"
        :label="label"
        :name="state"
    /></el-tabs>
    <div class="table-region">
      <el-table :data="visible" v-loading="loading"
        ><el-table-column
          prop="number"
          label="处方号"
          min-width="200"
        /><el-table-column
          prop="patientName"
          label="患者"
          width="120"
        /><el-table-column
          prop="doctor"
          label="开方医生"
          width="120"
        /><el-table-column label="状态" width="130"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 'DISPENSED' ? 'success' : 'info'">{{
              statuses[row.status]
            }}</el-tag></template
          ></el-table-column
        ><el-table-column
          prop="total_amount"
          label="金额（元）"
          width="110"
          align="right"
        /><el-table-column
          prop="allergy_notes"
          label="过敏史"
          min-width="150"
        /><el-table-column
          prop="created_at"
          label="开方时间"
          width="180"
        /><el-table-column label="操作" width="80" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="show(row)"
              >处理</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </div>
    <el-dialog v-model="open" title="录入门诊处方" width="1080px"
      ><el-form label-position="top"
        ><el-form-item label="门诊就诊" required
          ><el-select v-model="form.encounterId" filterable style="width: 420px"
            ><el-option
              v-for="e in encounters"
              :key="e.id"
              :value="e.id"
              :label="
                e.patientName + ' · ' + e.department + ' · ' + e.number
              " /></el-select></el-form-item></el-form
      ><el-table :data="form.items"
        ><el-table-column label="药品" min-width="190"
          ><template #default="{ row }"
            ><el-select
              v-model="row.itemId"
              filterable
              @change="selectDrug(row)"
              ><el-option
                v-for="d in drugs"
                :key="d.id"
                :value="d.id"
                :label="
                  d.name + ' ' + d.specification
                " /></el-select></template></el-table-column
        ><el-table-column label="发药单位" width="100"
          ><template #default="{ row }"
            ><el-select v-model="row.unitId"
              ><el-option
                v-for="u in row.units"
                :key="u.unitId"
                :value="u.unitId"
                :label="u.name" /></el-select></template></el-table-column
        ><el-table-column label="发药数量" width="95"
          ><template #default="{ row }"
            ><el-input v-model="row.quantity" /></template></el-table-column
        ><el-table-column label="单次剂量" width="100"
          ><template #default="{ row }"
            ><el-input v-model="row.dose" /></template></el-table-column
        ><el-table-column label="剂量单位" width="90"
          ><template #default="{ row }"
            ><el-select v-model="row.doseUnit"
              ><el-option
                v-for="u in ['mg', 'g', 'ml', '片', '支']"
                :key="u"
                :value="u"
                :label="u" /></el-select></template></el-table-column
        ><el-table-column label="每日次数" width="110"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.frequencyPerDay"
              :min="1"
              :max="24"
              controls-position="right"
              style="width: 95px" /></template></el-table-column
        ><el-table-column label="天数" width="95"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.days"
              :min="1"
              :max="90"
              controls-position="right"
              style="width: 80px" /></template></el-table-column
        ><el-table-column label="途径" width="100"
          ><template #default="{ row }"
            ><el-select v-model="row.route"
              ><el-option
                v-for="u in ['口服', '静滴', '肌注', '外用', '煎服']"
                :key="u"
                :value="u"
                :label="u" /></el-select></template></el-table-column
        ><el-table-column width="60"
          ><template #default="{ $index }"
            ><el-button link type="danger" @click="form.items.splice($index, 1)"
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-button style="margin-top: 16px" @click="addLine"
        ><Plus :size="16" /> 添加药品</el-button
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存草稿</el-button
        ></template
      ></el-dialog
    >
    <el-drawer v-model="detailOpen" title="处方处理" size="900px"
      ><template v-if="detail"
        ><div class="page-header">
          <div>
            <h2>{{ detail.patient.name }} · {{ detail.number }}</h2>
            <p class="muted">
              {{ detail.patient.allergy_notes || "未填写过敏史说明" }}
            </p>
          </div>
          <el-tag>{{ statuses[detail.status] }}</el-tag>
        </div>
        <div class="toolbar">
          <el-button @click="check"
            ><ShieldCheck :size="16" /> 用药审查</el-button
          ><el-button
            @click="window.open('/print/prescription/' + detail.id, '_blank')"
            ><Printer :size="16" /> 打印</el-button
          ><span style="margin-left: auto"
            >金额 ¥{{ detail.total_amount }}</span
          >
        </div>
        <div v-if="checkResult" style="margin-bottom: 16px">
          <el-alert
            :title="
              '审查结果：' +
              {
                CLEAR: '未命中规则',
                WARNING: '普通警告',
                BLOCKED: '严重问题阻断',
                INCOMPLETE: '资料不足',
              }[checkResult.status as 'CLEAR']
            "
            :type="
              checkResult.status === 'CLEAR'
                ? 'success'
                : checkResult.status === 'BLOCKED'
                  ? 'error'
                  : 'warning'
            "
            :closable="false"
          /><el-alert
            v-for="(r, i) in checkResult.results"
            :key="i"
            :title="r.message"
            :type="r.severity === 'BLOCK' ? 'error' : 'warning'"
            :closable="false"
            style="margin-top: 8px"
          />
        </div>
        <el-table :data="detail.items"
          ><el-table-column
            prop="name"
            label="药品"
            min-width="150"
          /><el-table-column
            prop="quantity"
            label="发药数量"
            width="95"
          /><el-table-column
            prop="unit"
            label="单位"
            width="65"
          /><el-table-column label="用法" min-width="160"
            ><template #default="{ row }"
              >{{ row.dose }}{{ row.dose_unit }} ·
              {{ row.frequency_per_day }}次/日 · {{ row.days }}天</template
            ></el-table-column
          ><el-table-column
            prop="amount"
            label="金额"
            width="90"
            align="right"
          /><el-table-column
            v-if="
              detail.status === 'DISPENSED' && auth.can('outpatient:review')
            "
            label="操作"
            width="70"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="returnDrug(row)"
                >退药</el-button
              ></template
            ></el-table-column
          ></el-table
        >
        <h2 style="margin: 24px 0 14px">操作轨迹</h2>
        <el-timeline
          ><el-timeline-item
            v-for="a in detail.actions"
            :key="a.id"
            :timestamp="a.occurred_at"
            >{{ a.operator }} · {{ a.action }}
            <p v-if="a.reason">{{ a.reason }}</p></el-timeline-item
          ></el-timeline
        ><template v-if="detail.returns.length"
          ><h2>退药记录</h2>
          <el-table :data="detail.returns"
            ><el-table-column prop="name" label="药品" /><el-table-column
              prop="base_qty"
              label="基础数量"
            /><el-table-column prop="amount" label="金额" /><el-table-column
              prop="refund_status"
              label="退款状态"
            /><el-table-column label="操作"
              ><template #default="{ row }"
                ><el-button
                  v-if="
                    row.refund_status === 'PENDING' &&
                    auth.can('clinical:payment')
                  "
                  link
                  type="primary"
                  @click="refund(row)"
                  >退款</el-button
                ></template
              ></el-table-column
            ></el-table
          ></template
        ></template
      ><template #footer
        ><template v-if="detail"
          ><el-button
            v-if="detail.status === 'DRAFT' && auth.can('clinical:write')"
            type="primary"
            :loading="saving"
            @click="action('submit')"
            >提交</el-button
          ><el-button
            v-if="
              detail.status === 'SUBMITTED' && auth.can('outpatient:review')
            "
            type="primary"
            :loading="saving"
            @click="action('price')"
            >审查并划价</el-button
          ><el-button
            v-if="detail.status === 'PRICED' && auth.can('clinical:payment')"
            type="primary"
            :loading="saving"
            @click="action('pay')"
            >确认缴费</el-button
          ><el-button
            v-if="detail.status === 'PAID' && auth.can('outpatient:review')"
            type="primary"
            :loading="saving"
            @click="action('verify')"
            >复核通过</el-button
          ><el-button
            v-if="
              detail.status === 'VERIFIED' && auth.can('outpatient:dispense')
            "
            type="primary"
            :loading="saving"
            @click="action('dispense')"
            >确认发药</el-button
          ><el-button
            v-if="
              !['DISPENSED', 'CANCELLED'].includes(detail.status) &&
              auth.can('clinical:write')
            "
            @click="action('cancel')"
            >取消处方</el-button
          ></template
        ></template
      ></el-drawer
    >
  </div>
</template>
