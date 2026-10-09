<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { Plus, RefreshCw, FlaskConical } from "lucide-vue-next";
import { ElMessageBox } from "element-plus";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
const auth = useAuth(),
  rows = ref<any[]>([]),
  formulas = ref<any[]>([]),
  drugs = ref<any[]>([]),
  loading = ref(false),
  saving = ref(false),
  open = ref(false),
  formulaOpen = ref(false),
  detailOpen = ref(false),
  detail = ref<any>(null),
  processOpen = ref(false),
  testOpen = ref(false),
  settleOpen = ref(false);
const form = reactive({
    formulaId: "",
    quantity: "100",
    batchNo: "",
    expiresOn: "",
  }),
  formula = reactive({
    code: "",
    name: "",
    outputItemId: "",
    outputQuantity: "100",
    standardHours: "2",
    standardEnergyCost: "20",
    items: [] as any[],
  }),
  process = reactive({
    stepNo: 1,
    name: "",
    hours: "1",
    outputQuantity: "100",
    energyCost: "10",
  }),
  test = reactive({
    standard: "卫生学检验合格，外观符合标准",
    measured: "符合标准",
    conclusion: "PASSED",
  }),
  settle = reactive({
    goodQuantity: "100",
    laborCost: "50",
    otherCost: "0",
    abnormalLossCost: "0",
  });
const materials = ref<any[]>([]),
  materialsOpen = ref(false);
async function showMaterials() {
  materials.value = await get(
    `/preparation/orders/${detail.value.id}/materials`,
  );
  materialsOpen.value = true;
}
async function returnMaterial(row: any) {
  const qty = await ElMessageBox.prompt(
    row.name + " · 剩余可退 " + row.remaining,
    "退回未消耗材料",
  );
  const reason = await ElMessageBox.prompt("请填写退料原因", "退料");
  await post(
    `/preparation/orders/${detail.value.id}/material-returns`,
    { allocationId: row.id, quantity: qty.value, reason: reason.value },
    crypto.randomUUID(),
  );
  await showMaterials();
  await show(detail.value);
}
const statuses: Record<string, string> = {
  DRAFT: "待审核",
  APPROVED: "已审核",
  PRODUCING: "生产中",
  COMPLETED: "已入库",
};
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/preparation/orders", {
      locationId: auth.locationId,
    });
    formulas.value = await get("/preparation/formulas");
  } finally {
    loading.value = false;
  }
}
async function create() {
  saving.value = true;
  try {
    await post("/preparation/orders", { ...form, locationId: auth.locationId });
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function createFormula() {
  saving.value = true;
  try {
    await post("/preparation/formulas", formula);
    formulaOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function show(row: any) {
  detail.value = await get("/preparation/orders/" + row.id);
  settle.goodQuantity = detail.value.planned_qty;
  detailOpen.value = true;
}
async function action(a: string, r: object = {}) {
  saving.value = true;
  try {
    await post(
      `/preparation/orders/${detail.value.id}/${a}`,
      r,
      crypto.randomUUID(),
    );
    processOpen.value = false;
    testOpen.value = false;
    settleOpen.value = false;
    await show(detail.value);
    await load();
  } finally {
    saving.value = false;
  }
}
async function confirm(a: string) {
  await ElMessageBox.confirm(
    a === "issue" ? "确认按批准配方领料并计入在制品？" : "确认审核通过？",
    "生产操作",
  );
  await action(a);
}
onMounted(async () => {
  await load();
  drugs.value = await get("/master/items");
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>制剂生产与质检</h1>
        <p class="muted">配方、生产工序、质量与成本</p>
      </div>
      <div>
        <el-button @click="load"><RefreshCw :size="16" />刷新</el-button
        ><el-button
          @click="
            formula.items = [{ itemId: '', quantity: '1' }];
            formulaOpen = true;
          "
          >新增配方</el-button
        ><el-button
          type="primary"
          @click="
            form.formulaId = formulas[0]?.id ?? '';
            open = true;
          "
          ><Plus :size="16" />生产订单</el-button
        >
      </div>
    </div>
    <div class="table-region">
      <el-table :data="rows" v-loading="loading"
        ><el-table-column
          prop="number"
          label="订单号"
          min-width="185"
        /><el-table-column
          prop="formulaName"
          label="配方"
          min-width="150"
        /><el-table-column prop="batch_no" label="生产批号" /><el-table-column
          prop="planned_qty"
          label="计划产量"
          align="right"
        /><el-table-column
          prop="good_qty"
          label="合格产量"
          align="right"
        /><el-table-column label="状态"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'">{{
              statuses[row.status]
            }}</el-tag></template
          ></el-table-column
        ><el-table-column
          prop="material_cost"
          label="材料成本"
          align="right"
        /><el-table-column
          prop="unit_cost"
          label="单位成本"
          align="right"
        /><el-table-column label="操作" width="80" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="show(row)"
              >详情</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </div>
    <el-dialog v-model="open" title="新增生产订单" width="560px"
      ><el-form label-position="top"
        ><el-form-item label="配方"
          ><el-select v-model="form.formulaId" style="width: 100%"
            ><el-option
              v-for="f in formulas"
              :key="f.id"
              :value="f.id"
              :label="f.name + ' · V' + f.revision" /></el-select
        ></el-form-item>
        <div class="form-grid">
          <el-form-item label="计划基础产量"
            ><el-input v-model="form.quantity" /></el-form-item
          ><el-form-item label="生产批号"
            ><el-input v-model="form.batchNo" /></el-form-item
          ><el-form-item label="有效期"
            ><el-date-picker
              v-model="form.expiresOn"
              type="date"
              value-format="YYYY-MM-DD"
          /></el-form-item></div></el-form
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="create"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="formulaOpen" title="新增配方版本" width="700px"
      ><el-form label-position="top"
        ><div class="form-grid">
          <el-form-item label="编码"
            ><el-input v-model="formula.code" /></el-form-item
          ><el-form-item label="配方名称"
            ><el-input v-model="formula.name" /></el-form-item
          ><el-form-item label="成品"
            ><el-select v-model="formula.outputItemId" filterable
              ><el-option
                v-for="d in drugs"
                :key="d.id"
                :value="d.id"
                :label="d.name" /></el-select></el-form-item
          ><el-form-item label="标准产量"
            ><el-input v-model="formula.outputQuantity" /></el-form-item
          ><el-form-item label="标准工时"
            ><el-input v-model="formula.standardHours" /></el-form-item
          ><el-form-item label="标准能源成本"
            ><el-input v-model="formula.standardEnergyCost"
          /></el-form-item></div></el-form
      ><el-table :data="formula.items"
        ><el-table-column label="投入材料"
          ><template #default="{ row }"
            ><el-select v-model="row.itemId" filterable
              ><el-option
                v-for="d in drugs"
                :key="d.id"
                :value="d.id"
                :label="d.name" /></el-select></template></el-table-column
        ><el-table-column label="标准基础数量"
          ><template #default="{ row }"
            ><el-input v-model="row.quantity" /></template></el-table-column
        ><el-table-column width="70"
          ><template #default="{ $index }"
            ><el-button
              link
              type="danger"
              @click="formula.items.splice($index, 1)"
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-button
        style="margin-top: 12px"
        @click="formula.items.push({ itemId: '', quantity: '1' })"
        >添加材料</el-button
      ><template #footer
        ><el-button @click="formulaOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="createFormula"
          >保存配方</el-button
        ></template
      ></el-dialog
    >
    <el-drawer v-model="detailOpen" title="生产订单详情" size="840px"
      ><template v-if="detail"
        ><div class="page-header">
          <h2>{{ detail.number }}</h2>
          <el-tag>{{ statuses[detail.status] }}</el-tag>
        </div>
        <el-descriptions border :column="3"
          ><el-descriptions-item label="批号">{{
            detail.batch_no
          }}</el-descriptions-item
          ><el-descriptions-item label="计划产量">{{
            detail.planned_qty
          }}</el-descriptions-item
          ><el-descriptions-item label="质检">{{
            detail.quality_status
          }}</el-descriptions-item
          ><el-descriptions-item label="材料成本">{{
            detail.material_cost
          }}</el-descriptions-item
          ><el-descriptions-item label="人工成本">{{
            detail.labor_cost
          }}</el-descriptions-item
          ><el-descriptions-item label="能源成本">{{
            detail.energy_cost
          }}</el-descriptions-item
          ><el-descriptions-item label="合格产量">{{
            detail.good_qty
          }}</el-descriptions-item
          ><el-descriptions-item label="单位成本">{{
            detail.unit_cost
          }}</el-descriptions-item
          ><el-descriptions-item label="有效期">{{
            detail.expires_on
          }}</el-descriptions-item></el-descriptions
        >
        <h2 style="margin: 24px 0 14px">生产工序</h2>
        <el-table :data="detail.processes"
          ><el-table-column
            prop="step_no"
            label="步骤"
            width="60" /><el-table-column
            prop="name"
            label="工序" /><el-table-column
            prop="actual_hours"
            label="实际工时" /><el-table-column
            prop="output_qty"
            label="产量" /><el-table-column
            prop="energy_cost"
            label="能源费用" /><el-table-column prop="operator" label="操作员"
        /></el-table>
        <h2 style="margin: 24px 0 14px">质检记录</h2>
        <el-table :data="detail.tests"
          ><el-table-column
            prop="standard_description"
            label="检查标准" /><el-table-column
            prop="measured_value"
            label="实测结果" /><el-table-column
            prop="conclusion"
            label="结论" /><el-table-column
            prop="tester"
            label="检测人" /></el-table></template
      ><template #footer
        ><template v-if="detail"
          ><el-button
            v-if="detail.status === 'DRAFT'"
            type="primary"
            :loading="saving"
            @click="confirm('approve')"
            >审核生产</el-button
          ><el-button
            v-if="detail.status === 'APPROVED'"
            type="primary"
            :loading="saving"
            @click="confirm('issue')"
            >执行领料</el-button
          ><template v-if="detail.status === 'PRODUCING'"
            ><el-button
              @click="
                process.stepNo = detail.processes.length + 1;
                processOpen = true;
              "
              >工序记录</el-button
            ><el-button @click="showMaterials">材料与退料</el-button
            ><el-button @click="testOpen = true">成品质检</el-button
            ><el-button type="primary" @click="settleOpen = true"
              >成本结算入库</el-button
            ></template
          ></template
        ></template
      ></el-drawer
    >
    <el-dialog v-model="processOpen" title="记录生产工序" width="520px"
      ><el-form label-position="top"
        ><el-form-item label="工序名称"
          ><el-input v-model="process.name"
        /></el-form-item>
        <div class="form-grid">
          <el-form-item label="实际工时"
            ><el-input v-model="process.hours" /></el-form-item
          ><el-form-item label="实际产量"
            ><el-input v-model="process.outputQuantity" /></el-form-item
          ><el-form-item label="能源费用（元）"
            ><el-input v-model="process.energyCost"
          /></el-form-item></div></el-form
      ><template #footer
        ><el-button
          type="primary"
          :loading="saving"
          @click="action('process', process)"
          >保存</el-button
        ></template
      ></el-dialog
    ><el-dialog v-model="testOpen" title="成品质检" width="560px"
      ><el-form label-position="top"
        ><el-form-item label="检查标准"
          ><el-input v-model="test.standard" type="textarea" /></el-form-item
        ><el-form-item label="实测值"
          ><el-input v-model="test.measured" type="textarea" /></el-form-item
        ><el-form-item label="结论"
          ><el-radio-group v-model="test.conclusion"
            ><el-radio value="PASSED">合格</el-radio
            ><el-radio value="FAILED">不合格</el-radio></el-radio-group
          ></el-form-item
        ></el-form
      ><template #footer
        ><el-button
          type="primary"
          :loading="saving"
          @click="action('test', test)"
          >提交质检</el-button
        ></template
      ></el-dialog
    ><el-dialog v-model="settleOpen" title="成本结算与放行入库" width="520px"
      ><el-form label-position="top"
        ><el-form-item label="合格基础产量"
          ><el-input v-model="settle.goodQuantity" /></el-form-item
        ><el-form-item label="人工费用（元）"
          ><el-input v-model="settle.laborCost" /></el-form-item
        ><el-form-item label="异常损失成本（元）"
          ><el-input v-model="settle.abnormalLossCost" /></el-form-item
        ><el-form-item label="其他费用（元）"
          ><el-input v-model="settle.otherCost" /></el-form-item></el-form
      ><template #footer
        ><el-button
          type="primary"
          :loading="saving"
          @click="action('settle', settle)"
          >确认结算入库</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="materialsOpen" title="生产投入材料及批次" width="800px"
      ><el-table :data="materials"
        ><el-table-column prop="name" label="材料" /><el-table-column
          prop="batch_no"
          label="批号"
        /><el-table-column prop="base_qty" label="领出数量" /><el-table-column
          prop="returned_qty"
          label="已退"
        /><el-table-column
          prop="cost_amount"
          label="领料成本"
        /><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button
              v-if="Number(row.remaining) > 0"
              link
              type="primary"
              @click="returnMaterial(row)"
              >退料</el-button
            ></template
          ></el-table-column
        ></el-table
      ></el-dialog
    >
  </div>
</template>
