<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import { Plus, Search, Printer, Download, RefreshCw } from "lucide-vue-next";
import { ElMessage, ElMessageBox } from "element-plus";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
const globalThis = window;
const auth = useAuth(),
  route = useRoute(),
  kind = computed(() =>
    route.path.includes("stocks")
      ? "stocks"
      : route.path.includes("ledger")
        ? "ledger"
        : "documents",
  );
const rows = ref<any[]>([]),
  loading = ref(false),
  keyword = ref(""),
  open = ref(false),
  detail = ref<any>(null),
  detailOpen = ref(false),
  saving = ref(false),
  drugs = ref<any[]>([]),
  options = ref<any>({ suppliers: [], units: [] }),
  locations = ref<any[]>([]);
const form = reactive({
  type: "RECEIPT",
  locationId: auth.locationId,
  targetLocationId: "",
  supplierId: "",
  reason: "",
  specialSource: "PURCHASE",
  items: [] as any[],
});
const types: Record<string, string> = {
  RECEIPT: "入库",
  TRANSFER: "调拨",
  LOSS: "报损",
  ISSUE: "领用出库",
  SUPPLIER_RETURN: "退供",
  DISPENSE: "发药",
  RETURN: "退药",
  STOCKTAKE: "盘点差异",
};
const statuses: Record<string, string> = {
  PARTIALLY_RECEIVED: "部分接收",
  REVERSED: "已冲销",
  DRAFT: "草稿",
  SUBMITTED: "待审核",
  APPROVED: "已审核",
  REJECTED: "已驳回",
  SHIPPED: "待接收",
  RECEIVED: "已接收",
  EXECUTED: "已执行",
  CANCELLED: "已取消",
};
const title = computed(() =>
  kind.value === "stocks"
    ? "批次库存"
    : kind.value === "ledger"
      ? "库存流水"
      : "库存单据",
);
const cost = computed(() =>
  rows.value.reduce((s, r) => s + Number(r.remaining_cost ?? 0), 0).toFixed(2),
);
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/inventory/" + kind.value, {
      locationId: auth.locationId,
      keyword: keyword.value,
    });
  } finally {
    loading.value = false;
  }
}
async function create() {
  form.type = route.path.includes("receipts") ? "RECEIPT" : "TRANSFER";
  form.reason = "";
  form.items = [];
  form.targetLocationId = "";
  form.supplierId = options.value.suppliers[0]?.id ?? "";
  addLine();
  open.value = true;
}
function addLine() {
  form.items.push({
    itemId: "",
    unitId: "",
    quantity: "1",
    batchNo: "",
    manufacturedOn: new Date().toISOString().slice(0, 10),
    expiresOn: "",
    unitCost: "0",
    unitOptions: [],
  });
}
async function selectDrug(line: any) {
  const d = await get("/master/items/" + line.itemId);
  line.unitOptions = d.units;
  line.unitId = d.units[0]?.unitId ?? "";
}
async function save() {
  if (!form.items.length || form.items.some((i) => !i.itemId)) {
    ElMessage.warning("请填写药品明细");
    return;
  }
  saving.value = true;
  try {
    await post("/inventory/documents", {
      ...form,
      locationId: auth.locationId,
      targetLocationId: form.type === "TRANSFER" ? form.targetLocationId : null,
      supplierId: ["RECEIPT", "SUPPLIER_RETURN"].includes(form.type)
        ? form.supplierId
        : null,
      items: form.items.map(({ unitOptions, ...i }) => i),
    });
    open.value = false;
    ElMessage.success("单据草稿已保存");
    await load();
  } finally {
    saving.value = false;
  }
}
async function show(row: any) {
  detail.value = await get("/inventory/documents/" + row.id);
  detailOpen.value = true;
}
async function action(a: string) {
  if (!detail.value) return;
  let reason = "";
  if (a === "reject") {
    const r = await ElMessageBox.prompt("请输入驳回原因", "驳回单据", {
      inputValidator: (v) => !!v?.trim() || "原因不能为空",
    });
    reason = r.value;
  } else
    await ElMessageBox.confirm(
      `确认${{ submit: "提交", approve: "审核通过", execute: "执行库存变更", receive: "接收全部药品", cancel: "取消单据" }[a as "submit"]}？`,
      "确认操作",
    );
  saving.value = true;
  try {
    await post(
      `/inventory/documents/${detail.value.id}/${a}`,
      { version: detail.value.version, reason },
      crypto.randomUUID(),
    );
    await show(detail.value);
    await load();
  } finally {
    saving.value = false;
  }
}
function print() {
  window.print();
}
async function reverse() {
  const r = await ElMessageBox.prompt(
    "整单冲销须由原执行人之外的人员确认。请填写原因。",
    "冲销单据",
  );
  await post(
    `/inventory/documents/${detail.value.id}/reversals`,
    { reason: r.value },
    crypto.randomUUID(),
  );
  await show(detail.value);
  await load();
}
async function partialReceive() {
  const allocations = await get(
    `/inventory/transfers/${detail.value.id}/allocations`,
  );
  const pending = allocations.filter((a: any) => Number(a.remaining_qty) > 0);
  const items = [];
  for (const a of pending) {
    const r = await ElMessageBox.prompt(
      a.name + " · " + a.batch_no + " · 剩余在途 " + a.remaining_qty,
      "分次接收",
      {
        inputValue: a.remaining_qty,
        inputPattern: /^\d+(\.\d{1,6})?$/,
        inputErrorMessage: "请输入有效数量",
      },
    );
    if (Number(r.value) > 0)
      items.push({ allocationId: a.id, quantity: r.value });
  }
  if (items.length) {
    await post(
      `/inventory/transfers/${detail.value.id}/receipts`,
      { items },
      crypto.randomUUID(),
    );
    await show(detail.value);
    await load();
  }
}
function exportData() {
  window.location.href = `/api/v1/reports/${kind.value}/export?locationId=${auth.locationId}`;
}
onMounted(async () => {
  await load();
  if (auth.can("master:read")) {
    drugs.value = await get("/master/items");
    options.value = await get("/master/options");
    locations.value = await get("/master/transfer-locations");
  }
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>{{ title }}</h1>
        <p class="muted">
          {{ auth.user?.locations.find((l) => l.id === auth.locationId)?.name }}
        </p>
      </div>
      <el-button
        v-if="kind === 'documents' && auth.can('inventory:write')"
        type="primary"
        @click="create"
        ><Plus :size="16" /> 新建单据</el-button
      >
    </div>
    <div class="filters no-print">
      <el-input
        v-model="keyword"
        placeholder="药品名称或编码"
        clearable
        style="width: 260px"
        @keyup.enter="load"
      /><el-button type="primary" @click="load"
        ><Search :size="16" /> 查询</el-button
      ><el-button
        @click="
          keyword = '';
          load();
        "
        ><RefreshCw :size="16" /> 重置</el-button
      >
    </div>
    <div class="toolbar">
      <span class="muted"
        >{{ rows.length }} 条记录<span v-if="kind === 'stocks'">
          · 库存采购成本 ¥{{ cost }}</span
        ></span
      >
      <div style="margin-left: auto; display: flex; gap: 8px">
        <el-button @click="exportData"><Download :size="16" /> 导出</el-button
        ><el-button @click="print"><Printer :size="16" /> 打印</el-button>
      </div>
    </div>
    <div class="table-region">
      <el-table :data="rows" v-loading="loading"
        ><template v-if="kind === 'stocks'"
          ><el-table-column
            prop="code"
            label="编码"
            width="100" /><el-table-column
            prop="name"
            label="药品名称"
            min-width="180" /><el-table-column
            prop="specification"
            label="规格"
            min-width="150" /><el-table-column
            prop="batch_no"
            label="批号"
            width="130" /><el-table-column
            prop="expires_on"
            label="有效期"
            width="110" /><el-table-column
            label="在手数量"
            width="110"
            align="right"
            ><template #default="{ row }"
              >{{ Number(row.on_hand_qty) }} {{ row.unit }}</template
            ></el-table-column
          ><el-table-column label="可用数量" width="110" align="right"
            ><template #default="{ row }"
              >{{ Number(row.available_qty) }} {{ row.unit }}</template
            ></el-table-column
          ><el-table-column label="采购成本" width="110" align="right"
            ><template #default="{ row }"
              >¥{{ Number(row.remaining_cost).toFixed(2) }}</template
            ></el-table-column
          ><el-table-column
            prop="stock_status"
            label="库存状态"
            width="120" /></template
        ><template v-else-if="kind === 'ledger'"
          ><el-table-column
            prop="occurred_at"
            label="时间"
            width="180" /><el-table-column
            prop="number"
            label="来源单据"
            min-width="180" /><el-table-column
            prop="name"
            label="药品"
            min-width="160" /><el-table-column
            prop="batch_no"
            label="批号"
            width="120" /><el-table-column
            label="数量变化"
            align="right"
            width="110"
            ><template #default="{ row }"
              ><span
                :style="{
                  color: Number(row.qty_delta) > 0 ? '#15803d' : '#b91c1c',
                }"
                >{{ Number(row.qty_delta) }} {{ row.unit }}</span
              ></template
            ></el-table-column
          ><el-table-column
            prop="balance_qty"
            label="结存数量"
            width="110"
            align="right" /><el-table-column
            prop="cost_delta"
            label="成本变化"
            width="110"
            align="right" /><el-table-column
            prop="operator"
            label="操作员"
            width="110" /></template
        ><template v-else
          ><el-table-column
            prop="number"
            label="单据号"
            min-width="210"
          /><el-table-column label="类型" width="100"
            ><template #default="{ row }">{{
              types[row.type] ?? row.type
            }}</template></el-table-column
          ><el-table-column
            prop="location"
            label="来源机构"
            min-width="140"
          /><el-table-column
            prop="target"
            label="目标机构"
            min-width="140"
          /><el-table-column label="状态" width="110"
            ><template #default="{ row }"
              ><el-tag
                :type="
                  ['EXECUTED', 'RECEIVED'].includes(row.status)
                    ? 'success'
                    : 'info'
                "
                >{{ statuses[row.status] ?? row.status }}</el-tag
              ></template
            ></el-table-column
          ><el-table-column
            prop="creator"
            label="制单人"
            width="110"
          /><el-table-column
            prop="business_date"
            label="业务日期"
            width="110"
          /><el-table-column label="操作" width="80" fixed="right"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="show(row)"
                >详情</el-button
              ></template
            ></el-table-column
          ></template
        ></el-table
      >
    </div>
    <el-dialog v-model="open" title="新建库存单据" width="1050px"
      ><el-form label-position="top"
        ><div class="form-grid">
          <el-form-item label="单据类型"
            ><el-select v-model="form.type"
              ><el-option
                v-for="(label, value) in {
                  RECEIPT: '入库',
                  TRANSFER: '调拨',
                  LOSS: '报损',
                  ISSUE: '领用出库',
                  SUPPLIER_RETURN: '退供',
                }"
                :key="value"
                :label="label"
                :value="value" /></el-select></el-form-item
          ><el-form-item
            v-if="form.type === 'TRANSFER'"
            label="接收机构"
            required
            ><el-select v-model="form.targetLocationId"
              ><el-option
                v-for="l in locations.filter((l) => l.id !== auth.locationId)"
                :key="l.id"
                :value="l.id"
                :label="l.name" /></el-select></el-form-item
          ><el-form-item
            v-if="['RECEIPT', 'SUPPLIER_RETURN'].includes(form.type)"
            label="供应商"
            ><el-select v-model="form.supplierId"
              ><el-option
                v-for="s in options.suppliers"
                :key="s.id"
                :label="s.name"
                :value="s.id" /></el-select></el-form-item
          ><el-form-item v-if="form.type === 'RECEIPT'" label="入库来源"
            ><el-select v-model="form.specialSource"
              ><el-option label="采购" value="PURCHASE" /><el-option
                label="赠送"
                value="GIFT" /><el-option
                label="实验药品"
                value="EXPERIMENT" /></el-select
          ></el-form-item>
        </div>
        <el-form-item label="原因与说明"
          ><el-input
            v-model="form.reason"
            maxlength="500" /></el-form-item></el-form
      ><el-table :data="form.items"
        ><el-table-column label="药品" min-width="180"
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
        ><el-table-column label="单位" width="100"
          ><template #default="{ row }"
            ><el-select v-model="row.unitId"
              ><el-option
                v-for="u in row.unitOptions"
                :key="u.unitId"
                :label="u.name"
                :value="u.unitId" /></el-select></template></el-table-column
        ><el-table-column label="数量" width="100"
          ><template #default="{ row }"
            ><el-input v-model="row.quantity" /></template></el-table-column
        ><template v-if="form.type === 'RECEIPT'"
          ><el-table-column label="批号" min-width="120"
            ><template #default="{ row }"
              ><el-input v-model="row.batchNo" /></template></el-table-column
          ><el-table-column label="生产日期" width="150"
            ><template #default="{ row }"
              ><el-date-picker
                v-model="row.manufacturedOn"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%" /></template></el-table-column
          ><el-table-column label="有效期" width="150"
            ><template #default="{ row }"
              ><el-date-picker
                v-model="row.expiresOn"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%" /></template></el-table-column
          ><el-table-column label="采购单价/选定单位" width="120"
            ><template #default="{ row }"
              ><el-input
                v-model="row.unitCost" /></template></el-table-column></template
        ><el-table-column width="60"
          ><template #default="{ $index }"
            ><el-button link type="danger" @click="form.items.splice($index, 1)"
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-button style="margin-top: 12px" @click="addLine"
        ><Plus :size="16" /> 添加明细</el-button
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存草稿</el-button
        ></template
      ></el-dialog
    >
    <el-drawer v-model="detailOpen" title="单据详情" size="760px"
      ><template v-if="detail"
        ><el-descriptions :column="2" border
          ><el-descriptions-item label="单据号">{{
            detail.number
          }}</el-descriptions-item
          ><el-descriptions-item label="类型">{{
            types[detail.type]
          }}</el-descriptions-item
          ><el-descriptions-item label="状态">{{
            statuses[detail.status]
          }}</el-descriptions-item
          ><el-descriptions-item label="业务日期">{{
            detail.business_date
          }}</el-descriptions-item
          ><el-descriptions-item label="说明" :span="2">{{
            detail.reason
          }}</el-descriptions-item></el-descriptions
        ><el-table :data="detail.items" style="margin-top: 20px"
          ><el-table-column prop="name" label="药品" /><el-table-column
            prop="quantity"
            label="数量" /><el-table-column
            prop="unit"
            label="单位" /><el-table-column
            prop="batch_no"
            label="批号" /><el-table-column prop="cost_amount" label="成本金额"
        /></el-table>
        <h2 style="margin: 24px 0 16px">操作轨迹</h2>
        <el-timeline
          ><el-timeline-item
            v-for="a in detail.actions"
            :key="a.id"
            :timestamp="a.occurred_at"
            >{{ a.operator }} · {{ a.action }}
            <p v-if="a.reason">{{ a.reason }}</p></el-timeline-item
          ></el-timeline
        ></template
      ><template #footer
        ><template v-if="detail"
          ><el-button
            @click="globalThis.open('/print/inventory/' + detail.id, '_blank')"
            >打印单据</el-button
          ><el-button
            v-if="
              detail.status === 'EXECUTED' &&
              ['RECEIPT', 'LOSS', 'ISSUE'].includes(detail.type) &&
              auth.can('inventory:approve')
            "
            type="danger"
            @click="reverse"
            >整单冲销</el-button
          ><el-button
            v-if="
              ['DRAFT', 'REJECTED'].includes(detail.status) &&
              auth.can('inventory:write')
            "
            @click="action('submit')"
            >提交审核</el-button
          ><el-button
            v-if="
              detail.status === 'SUBMITTED' && auth.can('inventory:approve')
            "
            @click="action('reject')"
            >驳回</el-button
          ><el-button
            v-if="
              detail.status === 'SUBMITTED' && auth.can('inventory:approve')
            "
            type="primary"
            :loading="saving"
            @click="action('approve')"
            >审核通过</el-button
          ><el-button
            v-if="detail.status === 'APPROVED' && auth.can('inventory:execute')"
            type="primary"
            :loading="saving"
            @click="action('execute')"
            >执行</el-button
          ><el-button
            v-if="
              ['SHIPPED', 'PARTIALLY_RECEIVED'].includes(detail.status) &&
              auth.user?.locations.some(
                (l) => l.id === detail.target_location_id,
              ) &&
              auth.can('inventory:execute')
            "
            type="primary"
            @click="partialReceive"
            >确认接收</el-button
          ></template
        ></template
      ></el-drawer
    >
  </div>
</template>
