<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
import { Plus, RefreshCw, PackageMinus } from "lucide-vue-next";
const auth = useAuth(),
  quotas = ref<any[]>([]),
  usage = ref<any[]>([]),
  items = ref<any[]>([]),
  stocks = ref<any[]>([]),
  open = ref(false),
  useOpen = ref(false),
  loading = ref(false),
  saving = ref(false),
  form = reactive({ itemId: "", quantity: "10" }),
  useForm = reactive({ itemId: "", quantity: "1", purpose: "" });
async function load() {
  loading.value = true;
  try {
    quotas.value = await get("/inpatient/ward-quotas", {
      locationId: auth.locationId,
    });
    usage.value = await get("/inpatient/ward-usage", {
      locationId: auth.locationId,
    });
    stocks.value = await get("/inventory/stocks", {
      locationId: auth.locationId,
    });
  } finally {
    loading.value = false;
  }
}
async function save() {
  saving.value = true;
  try {
    await post("/inpatient/ward-quotas", {
      ...form,
      locationId: auth.locationId,
    });
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function use() {
  saving.value = true;
  try {
    await post(
      "/inpatient/ward-usage",
      { ...useForm, locationId: auth.locationId },
      crypto.randomUUID(),
    );
    useOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
onMounted(async () => {
  await load();
  items.value = await get("/master/items");
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>病区基数药管理</h1>
      <div>
        <el-button @click="load"><RefreshCw :size="16" />刷新</el-button
        ><el-button @click="open = true"><Plus :size="16" />设置基数</el-button
        ><el-button type="primary" @click="useOpen = true"
          ><PackageMinus :size="16" />登记消耗</el-button
        >
      </div>
    </div>
    <el-alert
      v-if="
        auth.user?.locations.find((l) => l.id === auth.locationId)?.type !==
        'WARD'
      "
      title="请切换到病区库存机构"
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
    />
    <section class="section">
      <h2>核定基数</h2>
      <div class="table-region" style="margin-top: 16px">
        <el-table :data="quotas" v-loading="loading"
          ><el-table-column prop="name" label="药品" /><el-table-column
            prop="specification"
            label="规格" /><el-table-column
            prop="quota_qty"
            label="核定基础数量"
            align="right"
        /></el-table>
      </div>
    </section>
    <section class="section">
      <h2>病区库存</h2>
      <div class="table-region" style="margin-top: 16px">
        <el-table :data="stocks"
          ><el-table-column prop="name" label="药品" /><el-table-column
            prop="batch_no"
            label="批号" /><el-table-column
            prop="on_hand_qty"
            label="库存" /><el-table-column prop="unit" label="单位"
        /></el-table>
      </div>
    </section>
    <section class="section">
      <h2>消耗记录</h2>
      <div class="table-region" style="margin-top: 16px">
        <el-table :data="usage"
          ><el-table-column prop="used_at" label="时间" /><el-table-column
            prop="name"
            label="药品" /><el-table-column
            prop="base_qty"
            label="基础数量" /><el-table-column
            prop="purpose"
            label="用途" /><el-table-column prop="amount" label="成本（元）"
        /></el-table>
      </div>
    </section>
    <el-dialog v-model="open" title="设置病区基数" width="500px"
      ><el-form label-position="top"
        ><el-form-item label="药品"
          ><el-select v-model="form.itemId" filterable
            ><el-option
              v-for="i in items"
              :key="i.id"
              :value="i.id"
              :label="i.name" /></el-select></el-form-item
        ><el-form-item label="核定基础数量"
          ><el-input v-model="form.quantity" /></el-form-item></el-form
      ><template #footer
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    ><el-dialog v-model="useOpen" title="登记病区消耗" width="500px"
      ><el-form label-position="top"
        ><el-form-item label="药品"
          ><el-select v-model="useForm.itemId" filterable
            ><el-option
              v-for="i in items"
              :key="i.id"
              :value="i.id"
              :label="i.name" /></el-select></el-form-item
        ><el-form-item label="基础数量"
          ><el-input v-model="useForm.quantity" /></el-form-item
        ><el-form-item label="用途"
          ><el-input
            v-model="useForm.purpose"
            type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button type="primary" :loading="saving" @click="use"
          >确认消耗</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
