<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import { Plus, Printer, RefreshCw } from "lucide-vue-next";
import { ElMessageBox } from "element-plus";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
import ManualVoucher from "./ManualVoucher.vue";
const window = globalThis.window,
  auth = useAuth(),
  pricing = computed(() => useRoute().path.includes("pricing")),
  rows = ref<any[]>([]),
  payables = ref<any[]>([]),
  reconciliation = ref<any>(null),
  drugs = ref<any[]>([]),
  entries = ref<any[]>([]),
  loading = ref(false),
  open = ref(false),
  entryOpen = ref(false),
  tab = ref("vouchers"),
  saving = ref(false);
const form = reactive({
  itemId: "",
  newPrice: "",
  reason: "",
  confirmationMode: "MANUAL",
  effectiveAt: "",
});
async function load() {
  loading.value = true;
  try {
    if (pricing.value) rows.value = await get("/pricing/adjustments");
    else {
      rows.value = await get("/accounting/vouchers");
      payables.value = await get("/accounting/payables");
      reconciliation.value = await get("/accounting/reconciliation");
    }
  } finally {
    loading.value = false;
  }
}
async function save() {
  saving.value = true;
  try {
    await post("/pricing/adjustments", form);
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function confirm(row: any) {
  await ElMessageBox.confirm(
    "确认全院统一调价？已确认交易价格不会改变。",
    "调价确认",
  );
  await post(
    `/pricing/adjustments/${row.id}/${row.confirmation_mode === "AUTO" ? "approve" : "confirm"}`,
    {},
    crypto.randomUUID(),
  );
  await load();
}
async function pay(row: any) {
  const r = await ElMessageBox.prompt(
    "应付余额 ¥" + row.remaining,
    "供应商付款",
    { inputPattern: /^\d+(\.\d{1,2})?$/, inputErrorMessage: "请输入有效金额" },
  );
  await post(
    `/accounting/payables/${row.id}/pay`,
    { amount: r.value },
    crypto.randomUUID(),
  );
  await load();
}
async function details(row: any) {
  entries.value = await get("/accounting/vouchers/" + row.id);
  entryOpen.value = true;
}
async function postVoucher(row: any) {
  await ElMessageBox.confirm("确认凭证审核过账？", "凭证过账");
  await post(`/accounting/vouchers/${row.id}/post`, {}, crypto.randomUUID());
  await load();
}
onMounted(async () => {
  await load();
  if (pricing.value)
    drugs.value = (await get("/master/drugs", { pageSize: 100 })).records;
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ pricing ? "全院药品调价" : "药品会计与对账" }}</h1>
      <ManualVoucher
        v-if="!pricing && auth.can('accounting:write')"
        @saved="load"
      />
      <div>
        <el-button @click="load"><RefreshCw :size="16" />刷新</el-button
        ><el-button @click="window.print()"
          ><Printer :size="16" />打印</el-button
        ><el-button
          v-if="pricing && auth.can('pricing:write')"
          type="primary"
          @click="open = true"
          ><Plus :size="16" />新增调价</el-button
        >
      </div>
    </div>
    <template v-if="pricing"
      ><div class="table-region">
        <el-table :data="rows" v-loading="loading"
          ><el-table-column
            prop="number"
            label="单据号"
            min-width="190"
          /><el-table-column
            prop="name"
            label="药品"
            min-width="150"
          /><el-table-column
            prop="old_price"
            label="原基础售价"
            align="right"
          /><el-table-column
            prop="new_price"
            label="新基础售价"
            align="right"
          /><el-table-column
            prop="valuation_delta"
            label="库存估值差额"
            align="right"
          /><el-table-column
            prop="reason"
            label="调价原因"
            min-width="180"
          /><el-table-column label="状态"
            ><template #default="{ row }"
              ><el-tag>{{
                row.status === "EXECUTED" ? "已生效" : "待确认"
              }}</el-tag></template
            ></el-table-column
          ><el-table-column prop="creator" label="制单人" /><el-table-column
            label="操作"
            ><template #default="{ row }"
              ><el-button
                v-if="row.status === 'SUBMITTED'"
                link
                type="primary"
                @click="confirm(row)"
                >{{
                  row.confirmation_mode === "AUTO" ? "批准定时生效" : "确认"
                }}</el-button
              ></template
            ></el-table-column
          ></el-table
        >
      </div></template
    ><template v-else
      ><div v-if="reconciliation" class="summary-band">
        <div class="summary-item">
          <span class="muted">库存采购成本（元）</span>
          <div class="summary-value">{{ reconciliation.inventoryCost }}</div>
        </div>
        <div class="summary-item">
          <span class="muted">流水成本（元）</span>
          <div class="summary-value">
            {{ reconciliation.inventoryLedgerCost }}
          </div>
        </div>
        <div class="summary-item">
          <span class="muted">凭证借贷差额（元）</span>
          <div class="summary-value">
            {{ reconciliation.voucherDifference }}
          </div>
        </div>
        <div class="summary-item">
          <span class="muted">待付账款</span>
          <div class="summary-value">
            {{ payables.filter((p) => Number(p.remaining) > 0).length }}
          </div>
        </div>
      </div>
      <el-tabs v-model="tab"
        ><el-tab-pane label="会计凭证" name="vouchers" /><el-tab-pane
          label="应付与付款"
          name="payables" /><el-tab-pane
          label="发药收费对账"
          name="reconciliation"
      /></el-tabs>
      <div class="table-region">
        <el-table v-if="tab === 'vouchers'" :data="rows" v-loading="loading"
          ><el-table-column
            prop="number"
            label="凭证号"
            min-width="180"
          /><el-table-column
            prop="business_date"
            label="日期"
            width="120"
          /><el-table-column
            prop="description"
            label="摘要"
            min-width="180"
          /><el-table-column
            prop="debit"
            label="借方合计"
            align="right"
          /><el-table-column
            prop="credit"
            label="贷方合计"
            align="right"
          /><el-table-column label="操作"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="details(row)"
                >分录</el-button
              ><el-button
                v-if="row.status === 'DRAFT' && auth.can('accounting:write')"
                link
                type="primary"
                @click="postVoucher(row)"
                >审核过账</el-button
              ></template
            ></el-table-column
          ></el-table
        ><el-table v-else-if="tab === 'payables'" :data="payables"
          ><el-table-column
            prop="supplier"
            label="供应商"
            min-width="170"
          /><el-table-column
            prop="documentNumber"
            label="入库单"
            min-width="180"
          /><el-table-column
            prop="amount"
            label="应付总额"
            align="right"
          /><el-table-column
            prop="paid_amount"
            label="已付金额"
            align="right"
          /><el-table-column
            prop="remaining"
            label="余额"
            align="right"
          /><el-table-column label="操作"
            ><template #default="{ row }"
              ><el-button
                v-if="Number(row.remaining) > 0 && auth.can('accounting:write')"
                link
                type="primary"
                @click="pay(row)"
                >付款</el-button
              ></template
            ></el-table-column
          ></el-table
        ><el-table v-else :data="reconciliation?.prescriptions"
          ><el-table-column
            prop="number"
            label="处方号"
            min-width="180" /><el-table-column
            prop="total_amount"
            label="划价金额"
            align="right" /><el-table-column
            prop="paid_amount"
            label="实收金额"
            align="right" /><el-table-column
            prop="refunded_amount"
            label="已退款"
            align="right" /><el-table-column prop="difference" label="对账分类"
        /></el-table></div></template
    ><el-dialog v-model="open" title="新建调价单" width="550px"
      ><el-form label-position="top"
        ><el-form-item label="药品"
          ><el-select v-model="form.itemId" filterable style="width: 100%"
            ><el-option
              v-for="d in drugs"
              :key="d.id"
              :value="d.id"
              :label="
                d.name + ' · 原售价 ' + d.retailPrice
              " /></el-select></el-form-item
        ><el-form-item label="新零售价（元/基础单位）"
          ><el-input v-model="form.newPrice" /></el-form-item
        ><el-form-item label="确认模式"
          ><el-radio-group v-model="form.confirmationMode"
            ><el-radio value="MANUAL">手动确认</el-radio
            ><el-radio value="AUTO">批准后定时生效</el-radio></el-radio-group
          ></el-form-item
        ><el-form-item v-if="form.confirmationMode === 'AUTO'" label="生效时间"
          ><el-date-picker
            v-model="form.effectiveAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
        ><el-form-item label="调价文件或原因"
          ><el-input
            v-model="form.reason"
            type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >提交</el-button
        ></template
      ></el-dialog
    ><el-dialog v-model="entryOpen" title="凭证分录" width="700px"
      ><el-table :data="entries"
        ><el-table-column prop="account" label="会计科目" /><el-table-column
          prop="location"
          label="机构" /><el-table-column
          prop="debit"
          label="借方"
          align="right" /><el-table-column
          prop="credit"
          label="贷方"
          align="right" /></el-table
    ></el-dialog>
  </div>
</template>
