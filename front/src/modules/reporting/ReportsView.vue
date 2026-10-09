<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from "vue";
import * as echarts from "echarts/core";
import { BarChart } from "echarts/charts";
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
echarts.use([
  BarChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);
import { get } from "../../api/client";
import { useAuth } from "../../stores/auth";
import { Printer, Download, Search } from "lucide-vue-next";
const window = globalThis.window,
  auth = useAuth(),
  data = ref<any>(null),
  loading = ref(false),
  chartElement = ref<HTMLElement>(),
  from = ref(
    new Date(
      new Date().getFullYear(),
      new Date().getMonth(),
      1,
    ).toLocaleDateString("en-CA"),
  ),
  to = ref(new Date().toLocaleDateString("en-CA"));
let chart: echarts.ECharts | undefined;
async function load() {
  loading.value = true;
  try {
    data.value = await get("/reports/summary", {
      locationId: auth.locationId,
      from: from.value,
      to: to.value,
    });
    await nextTick();
    if (chartElement.value) {
      chart ??= echarts.init(chartElement.value);
      chart.setOption({
        color: ["#2563eb", "#15803d"],
        tooltip: { trigger: "axis" },
        legend: { data: ["入库成本", "出库成本"] },
        grid: { left: 55, right: 20, top: 40, bottom: 35 },
        xAxis: {
          type: "category",
          data: data.value.daily.map((r: any) => r.date),
        },
        yAxis: { type: "value" },
        series: [
          {
            name: "入库成本",
            type: "bar",
            data: data.value.daily.map((r: any) => Number(r.incoming)),
          },
          {
            name: "出库成本",
            type: "bar",
            data: data.value.daily.map((r: any) => Number(r.outgoing)),
          },
        ],
      });
    }
  } finally {
    loading.value = false;
  }
}
function resize() {
  chart?.resize();
}
onMounted(() => {
  load();
  window.addEventListener("resize", resize);
});
onBeforeUnmount(() => {
  chart?.dispose();
  window.removeEventListener("resize", resize);
});
</script>
<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <h1>药品统计报表</h1>
      <el-button @click="window.print()"><Printer :size="16" />打印</el-button>
    </div>
    <div class="filters">
      <el-date-picker
        v-model="from"
        type="date"
        value-format="YYYY-MM-DD"
      /><span>至</span
      ><el-date-picker
        v-model="to"
        type="date"
        value-format="YYYY-MM-DD"
      /><el-button type="primary" @click="load"
        ><Search :size="16" />查询</el-button
      >
    </div>
    <template v-if="data"
      ><div class="summary-band">
        <div class="summary-item">
          <span class="muted">入库成本（元）</span>
          <div class="summary-value">{{ data.incoming }}</div>
        </div>
        <div class="summary-item">
          <span class="muted">出库成本（元）</span>
          <div class="summary-value">{{ data.outgoing }}</div>
        </div>
        <div class="summary-item">
          <span class="muted">期末库存成本（元）</span>
          <div class="summary-value">{{ data.currentCost }}</div>
        </div>
        <div class="summary-item">
          <span class="muted">处方发药量</span>
          <div class="summary-value">{{ data.prescriptionCount }}</div>
        </div>
      </div>
      <section class="section">
        <h2>每日库存成本变化</h2>
        <div
          ref="chartElement"
          style="height: 300px; width: 100%; margin-top: 12px"
        />
      </section>
      <section class="section">
        <h2 style="margin-bottom: 16px">药品出入存汇总</h2>
        <div class="table-region">
          <el-table :data="data.items"
            ><el-table-column
              prop="name"
              label="药品"
              min-width="180" /><el-table-column
              prop="specification"
              label="规格" /><el-table-column
              prop="unit"
              label="基础单位"
              width="90" /><el-table-column
              prop="incomingQty"
              label="入库数量"
              align="right" /><el-table-column
              prop="outgoingQty"
              label="出库数量"
              align="right" /><el-table-column
              prop="cost"
              label="净成本变化"
              align="right"
          /></el-table>
        </div></section
    ></template>
  </div>
</template>
