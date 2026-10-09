<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
import { ElMessageBox } from "element-plus";
const auth = useAuth(),
  raw = ref<any[]>([]),
  tests = ref<any[]>([]),
  sales = ref<any[]>([]),
  items = ref<any[]>([]),
  testOpen = ref(false),
  saleOpen = ref(false),
  tab = ref("raw"),
  saving = ref(false);
const test = reactive({
    stockId: "",
    standard: "外观、纯度与卫生学符合教学标准",
    measured: "符合标准",
    conclusion: "PASSED",
  }),
  sale = reactive({
    itemId: "6",
    customerName: "",
    quantity: "1",
    unitPrice: "15",
  });
async function load() {
  raw.value = await get("/preparation/raw-quality", {
    locationId: auth.locationId,
  });
  tests.value = await get("/preparation/raw-tests", {
    locationId: auth.locationId,
  });
  sales.value = await get("/preparation/sales", {
    locationId: auth.locationId,
  });
}
async function saveTest() {
  saving.value = true;
  try {
    await post("/preparation/raw-tests", test, crypto.randomUUID());
    testOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function confirmTest(row: any) {
  await ElMessageBox.confirm(
    "确认质检结论并变更原料库存状态？",
    "独立质检放行",
  );
  await post(
    `/preparation/raw-tests/${row.id}/confirm`,
    {},
    crypto.randomUUID(),
  );
  await load();
}
async function saveSale() {
  saving.value = true;
  try {
    await post("/preparation/sales", { ...sale, locationId: auth.locationId });
    saleOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function executeSale(row: any) {
  await ElMessageBox.confirm(
    "确认销售已收款并出库？金额 ¥" + row.amount,
    "销售确认",
  );
  await post(`/preparation/sales/${row.id}/execute`, {}, crypto.randomUUID());
  await load();
}
onMounted(async () => {
  await load();
  items.value = await get("/master/items");
});
</script>
<template>
  <section class="section" style="margin-top: 24px">
    <el-tabs v-model="tab"
      ><el-tab-pane label="原料待检" name="raw" /><el-tab-pane
        label="原料质检记录"
        name="tests" /><el-tab-pane label="成品销售" name="sales"
    /></el-tabs>
    <div class="toolbar">
      <el-button v-if="tab === 'sales'" type="primary" @click="saleOpen = true"
        >新增销售</el-button
      ><el-button @click="load">刷新</el-button>
    </div>
    <div class="table-region">
      <el-table v-if="tab === 'raw'" :data="raw"
        ><el-table-column prop="name" label="原料名称" /><el-table-column
          prop="batch_no"
          label="批号"
        /><el-table-column
          prop="on_hand_qty"
          label="待检数量"
        /><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              @click="
                test.stockId = row.id;
                testOpen = true;
              "
              >填写质检</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-table v-else-if="tab === 'tests'" :data="tests"
        ><el-table-column prop="name" label="原料名称" /><el-table-column
          prop="batch_no"
          label="批号"
        /><el-table-column
          prop="standard_description"
          label="标准"
        /><el-table-column prop="measured_value" label="实测" /><el-table-column
          prop="conclusion"
          label="结论"
        /><el-table-column prop="tester" label="检测人" /><el-table-column
          label="操作"
          ><template #default="{ row }"
            ><el-button
              v-if="row.status === 'SUBMITTED'"
              link
              type="primary"
              @click="confirmTest(row)"
              >独立放行</el-button
            ><span v-else>已确认</span></template
          ></el-table-column
        ></el-table
      ><el-table v-else :data="sales"
        ><el-table-column prop="name" label="销售品种" /><el-table-column
          prop="customer_name"
          label="客户"
        /><el-table-column prop="quantity" label="基础数量" /><el-table-column
          prop="amount"
          label="金额"
        /><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button
              v-if="row.status === 'DRAFT'"
              link
              type="primary"
              @click="executeSale(row)"
              >确认收款出库</el-button
            ><span v-else>已执行</span></template
          ></el-table-column
        ></el-table
      >
    </div>
    <el-dialog v-model="testOpen" title="原料质检" width="540px"
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
        ><el-button type="primary" :loading="saving" @click="saveTest"
          >提交检测</el-button
        ></template
      ></el-dialog
    ><el-dialog v-model="saleOpen" title="新增制剂销售" width="540px"
      ><el-form label-position="top"
        ><el-form-item label="销售品种"
          ><el-select v-model="sale.itemId" filterable
            ><el-option
              v-for="i in items"
              :key="i.id"
              :value="i.id"
              :label="i.name" /></el-select></el-form-item
        ><el-form-item label="客户"
          ><el-input v-model="sale.customerName" /></el-form-item
        ><el-form-item label="基础数量"
          ><el-input v-model="sale.quantity" /></el-form-item
        ><el-form-item label="单价（元/基础单位）"
          ><el-input v-model="sale.unitPrice" /></el-form-item></el-form
      ><template #footer
        ><el-button type="primary" :loading="saving" @click="saveSale"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>
