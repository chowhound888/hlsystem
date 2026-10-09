<script setup lang="ts">
import { ref, onMounted } from "vue";
import { get } from "../../api/client";
import { Printer, Search } from "lucide-vue-next";
const window = globalThis.window,
  data = ref<any>(null),
  from = ref(
    new Date(
      new Date().getFullYear(),
      new Date().getMonth(),
      1,
    ).toLocaleDateString("en-CA"),
  ),
  to = ref(new Date().toLocaleDateString("en-CA")),
  loading = ref(false),
  tab = ref("departments");
async function load() {
  loading.value = true;
  try {
    data.value = await get("/reports/analysis", {
      from: from.value,
      to: to.value,
    });
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <h1>全院药品会计统计</h1>
      <el-button @click="window.print()"><Printer :size="16" />打印</el-button>
    </div>
    <div class="filters">
      <el-date-picker
        v-model="from"
        value-format="YYYY-MM-DD"
        type="date"
      /><span>至</span
      ><el-date-picker
        v-model="to"
        value-format="YYYY-MM-DD"
        type="date"
      /><el-button type="primary" @click="load"
        ><Search :size="16" />查询</el-button
      >
    </div>
    <template v-if="data"
      ><div class="summary-band">
        <div class="summary-item">
          <span class="muted">实际销售净额（元）</span>
          <div class="summary-value">{{ data.income }}</div>
        </div>
        <div class="summary-item">
          <span class="muted">净发药销售成本（元）</span>
          <div class="summary-value">{{ data.cost }}</div>
        </div>
        <div class="summary-item">
          <span class="muted">实际综合加成率（%）</span>
          <div class="summary-value">{{ data.markupRate }}</div>
        </div>
        <div class="summary-item">
          <span class="muted">完成生产批次</span>
          <div class="summary-value">{{ data.production.length }}</div>
        </div>
      </div>
      <el-tabs v-model="tab"
        ><el-tab-pane label="科室门诊消耗" name="departments" /><el-tab-pane
          label="月度收支"
          name="months" /><el-tab-pane label="制剂成本" name="production"
      /></el-tabs>
      <div class="table-region">
        <el-table v-if="tab === 'departments'" :data="data.departments"
          ><el-table-column prop="department" label="科室" /><el-table-column
            prop="prescriptionCount"
            label="处方量"
            align="right" /><el-table-column
            prop="consumptionAmount"
            label="净消耗金额（元）"
            align="right" /></el-table
        ><el-table v-else-if="tab === 'months'" :data="data.months"
          ><el-table-column prop="month" label="月份" /><el-table-column
            prop="income"
            label="销售净额（元）"
            align="right" /><el-table-column
            prop="cost"
            label="消耗成本（元）"
            align="right" /></el-table
        ><el-table v-else :data="data.production"
          ><el-table-column
            prop="number"
            label="生产单号"
            min-width="190" /><el-table-column
            prop="name"
            label="配方"
            min-width="160" /><el-table-column
            prop="batch_no"
            label="批号"
            min-width="140" /><el-table-column
            prop="good_qty"
            label="合格产量" /><el-table-column
            prop="material_cost"
            label="净材料成本" /><el-table-column
            prop="labor_cost"
            label="人工成本" /><el-table-column
            prop="energy_cost"
            label="能源成本" /><el-table-column
            prop="abnormal_loss_cost"
            label="异常损失" /><el-table-column
            prop="unit_cost"
            label="单位成本"
        /></el-table></div
    ></template>
  </div>
</template>
