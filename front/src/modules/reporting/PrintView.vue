<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { get } from "../../api/client";
import { Printer, ArrowLeft } from "lucide-vue-next";
const window = globalThis.window,
  route = useRoute(),
  data = ref<any>(null),
  kind = String(route.params.kind),
  template = ref<any>(null),
  fields = ref<string[]>([]);
const columns: Record<string, string> = {
  name: "药品名称",
  specification: "规格",
  unit: "单位",
  quantity: "数量",
  batch_no: "批号",
  cost_amount: "成本金额（元）",
  directions: "用法",
  amount: "处方金额（元）",
};
function value(line: any, key: string) {
  return key === "directions"
    ? `${line.dose}${line.dose_unit}，${line.frequency_per_day}次/日，${line.days}天`
    : (line[key] ?? "");
}
const labels: Record<string, string> = {
  RECEIPT: "药品入库单",
  TRANSFER: "药品调拨单",
  LOSS: "药品报损单",
  ISSUE: "药品领用单",
  SUPPLIER_RETURN: "药品退供单",
  DISPENSE: "发药单",
  RETURN: "退药单",
  STOCKTAKE: "盘点差异单",
};
onMounted(async () => {
  data.value = await get(
    kind === "prescription"
      ? `/outpatient/prescriptions/${route.params.id}`
      : `/inventory/documents/${route.params.id}`,
  );
  const templates = await get<any[]>("/system/print-templates");
  template.value = templates.find(
    (t) =>
      t.document_type ===
      (kind === "prescription" ? "PRESCRIPTION" : "INVENTORY"),
  );
  fields.value = template.value
    ? JSON.parse(template.value.fields_json)
    : ["name", "specification", "unit", "quantity", "batch_no", "cost_amount"];
  if (template.value?.paper_orientation === "landscape") {
    const style = document.createElement("style");
    style.textContent = "@media print{@page{size:A4 landscape}}";
    document.head.append(style);
  }
});
</script>
<template>
  <main class="print-page">
    <div class="no-print print-controls">
      <el-button @click="window.history.back()"
        ><ArrowLeft :size="16" />返回</el-button
      ><el-button type="primary" @click="window.print()"
        ><Printer :size="16" />打印</el-button
      >
    </div>
    <template v-if="data"
      ><header>
        <p>仁和教学医院</p>
        <h1>
          {{
            template?.title ??
            (kind === "prescription"
              ? "门诊药品处方单"
              : (labels[data.type] ?? "药品业务单据"))
          }}
        </h1>
      </header>
      <div class="print-meta">
        <span>单据号：{{ data.number }}</span
        ><span
          >日期：{{ data.business_date ?? data.created_at?.slice(0, 10) }}</span
        ><template v-if="kind === 'prescription'"
          ><span>患者：{{ data.patient.name }}</span
          ><span>处方金额：¥{{ data.total_amount }}</span></template
        ><span v-else>说明：{{ data.reason }}</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>序号</th>
            <th v-for="f in fields" :key="f">{{ columns[f] }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(line, index) in data.items" :key="line.id">
            <td>{{ index + 1 }}</td>
            <td v-for="f in fields" :key="f">{{ value(line, f) }}</td>
          </tr>
        </tbody>
      </table>
      <div class="signature">
        <span>制单人：__________</span><span>审核人：__________</span
        ><span>发药/收货人：__________</span>
      </div>
      <p class="print-note">
        单据号：{{ data.number }}　打印时间：{{
          new Date().toLocaleString("zh-CN")
        }}　模板版本：{{ template?.revision ?? 1 }}
      </p></template
    ><el-skeleton v-else :rows="8" animated />
  </main>
</template>
<style scoped>
.print-page {
  max-width: 1000px;
  padding: 30px;
  margin: auto;
  background: #fff;
  min-height: 100vh;
}
.print-controls {
  display: flex;
  gap: 12px;
  margin-bottom: 32px;
}
header {
  text-align: center;
}
header p {
  font-size: 16px;
}
header h1 {
  font-size: 24px;
}
.print-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  margin: 28px 0 16px;
  font-size: 13px;
}
table {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
}
th,
td {
  border: 1px solid #444;
  padding: 9px;
  text-align: left;
}
th {
  background: #f5f6f8;
}
.signature {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 40px;
  font-size: 13px;
}
.print-note {
  font-size: 11px;
  margin-top: 30px;
  color: #59616d;
}
@media print {
  .print-page {
    padding: 0;
    max-width: none;
    min-height: auto;
  }
  .print-controls {
    display: none;
  }
  @page {
    size: A4;
    margin: 16mm;
  }
  thead {
    display: table-header-group;
  }
  tr {
    break-inside: avoid;
  }
  .signature {
    break-inside: avoid;
  }
}
</style>
