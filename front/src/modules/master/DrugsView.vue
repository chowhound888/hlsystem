<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { Plus, Search, RefreshCw, Printer } from "lucide-vue-next";
import { ElMessage, ElMessageBox } from "element-plus";
import { get, post, put } from "../../api/client";
import { useAuth } from "../../stores/auth";
const auth = useAuth(),
  rows = ref<any[]>([]),
  total = ref(0),
  loading = ref(false),
  saving = ref(false),
  open = ref(false),
  editId = ref(""),
  keyword = ref(""),
  page = ref(1),
  options = ref<any>({ units: [], manufacturers: [] });
const defaults = () => ({
  code: "",
  name: "",
  specification: "",
  baseUnitId: "",
  manufacturerId: "",
  dosageForm: "片剂",
  category: "普通药品",
  tradeName: "",
  insuranceCode: "",
  insuranceCategory: "A",
  controlLevel: "NORMAL",
  prescriptionRequired: true,
  imported: false,
  inHouse: false,
  selfPay: false,
  valuable: false,
  retailPrice: "0",
  quantityScale: 0,
  packagingUnitId: null as string | null,
  packagingFactor: "1",
  aliases: [] as string[],
  version: 0,
});
const form = reactive(defaults()),
  aliasText = ref("");
const controls: Record<string, string> = {
  NORMAL: "普通药品",
  TOXIC: "毒性药品",
  NARCOTIC: "麻醉药品",
  PSYCHOTROPIC: "精神药品",
};
async function load() {
  loading.value = true;
  try {
    const r = await get("/master/drugs", {
      keyword: keyword.value,
      page: page.value,
      pageSize: 20,
    });
    rows.value = r.records;
    total.value = Number(r.total);
  } finally {
    loading.value = false;
  }
}
async function edit(row?: any) {
  Object.assign(form, defaults());
  aliasText.value = "";
  editId.value = row?.id ?? "";
  if (row) {
    const r = await get("/master/drugs/" + row.id);
    Object.assign(form, {
      ...r,
      baseUnitId: r.base_unit_id,
      quantityScale: r.quantity_scale,
    });
    aliasText.value = r.aliases.join("，");
    const pack = r.units.find((u: any) => u.unitId !== r.base_unit_id);
    if (pack) {
      form.packagingUnitId = pack.unitId;
      form.packagingFactor = pack.factor;
    }
  } else {
    form.baseUnitId = options.value.units[0]?.id ?? "";
    form.manufacturerId = options.value.manufacturers[0]?.id ?? "";
  }
  open.value = true;
}
async function save() {
  if (!form.code || !form.name || !form.specification) {
    ElMessage.warning("请填写编码、名称和规格");
    return;
  }
  saving.value = true;
  try {
    const data = {
      ...form,
      aliases: aliasText.value
        .split(/[,，]/)
        .map((s) => s.trim())
        .filter(Boolean),
    };
    if (editId.value) await put("/master/drugs/" + editId.value, data);
    else await post("/master/drugs", data);
    ElMessage.success("药品已保存");
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function toggle(row: any) {
  await ElMessageBox.confirm(
    `确认${row.enabled ? "停用" : "启用"} ${row.name}？`,
    "药品状态",
  );
  await post("/master/drugs/" + row.id + "/enabled", { enabled: !row.enabled });
  await load();
}
onMounted(async () => {
  options.value = await get("/master/options");
  await load();
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>药品字典</h1>
        <p class="muted">药品品种、规格、单位及医保资料</p>
      </div>
      <el-button v-if="auth.can('master:write')" type="primary" @click="edit()"
        ><Plus :size="16" /> 新增药品</el-button
      >
    </div>
    <div class="filters">
      <el-input
        v-model="keyword"
        placeholder="药品名称、编码、商品名或别名"
        clearable
        style="width: 320px"
        @keyup.enter="
          page = 1;
          load();
        "
      /><el-button
        type="primary"
        @click="
          page = 1;
          load();
        "
        ><Search :size="16" /> 查询</el-button
      ><el-button
        @click="
          keyword = '';
          page = 1;
          load();
        "
        ><RefreshCw :size="16" /> 重置</el-button
      >
    </div>
    <div class="toolbar">
      <span class="muted">共 {{ total }} 个药品规格</span>
    </div>
    <div class="table-region">
      <el-table :data="rows" v-loading="loading"
        ><el-table-column
          prop="code"
          label="编码"
          width="110"
        /><el-table-column
          prop="name"
          label="药品名称"
          min-width="170"
        /><el-table-column
          prop="specification"
          label="规格"
          min-width="160"
        /><el-table-column
          prop="dosageForm"
          label="剂型"
          width="80"
        /><el-table-column
          prop="manufacturer"
          label="生产厂家"
          min-width="170"
        /><el-table-column
          prop="baseUnit"
          label="发药单位"
          width="90"
        /><el-table-column label="零售价/基础单位" width="140" align="right"
          ><template #default="{ row }"
            >¥{{ Number(row.retailPrice).toFixed(2) }}</template
          ></el-table-column
        ><el-table-column label="控制级别" width="100"
          ><template #default="{ row }"
            ><el-tag
              :type="row.controlLevel === 'NORMAL' ? 'info' : 'warning'"
              >{{ controls[row.controlLevel] }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column label="状态" width="80"
          ><template #default="{ row }"
            ><el-tag :type="row.enabled ? 'success' : 'info'">{{
              row.enabled ? "启用" : "停用"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column
          v-if="auth.can('master:write')"
          label="操作"
          width="140"
          fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="edit(row)">编辑</el-button
            ><el-button
              link
              :type="row.enabled ? 'danger' : 'success'"
              @click="toggle(row)"
              >{{ row.enabled ? "停用" : "启用" }}</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </div>
    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        :page-size="20"
        :total="total"
        layout="prev,pager,next,total"
        @current-change="load"
      />
    </div>
    <el-drawer
      v-model="open"
      :title="editId ? '编辑药品' : '新增药品'"
      size="640px"
      ><el-form :model="form" label-position="top"
        ><div class="form-grid">
          <el-form-item label="药品编码" required
            ><el-input v-model="form.code" maxlength="64" /></el-form-item
          ><el-form-item label="药品名称" required
            ><el-input v-model="form.name" /></el-form-item
          ><el-form-item label="规格" required
            ><el-input v-model="form.specification" /></el-form-item
          ><el-form-item label="商品名"
            ><el-input v-model="form.tradeName" /></el-form-item
          ><el-form-item label="基础发药单位" required
            ><el-select v-model="form.baseUnitId" :disabled="!!editId"
              ><el-option
                v-for="u in options.units"
                :key="u.id"
                :value="u.id"
                :label="u.name" /></el-select></el-form-item
          ><el-form-item label="生产厂家" required
            ><el-select v-model="form.manufacturerId"
              ><el-option
                v-for="m in options.manufacturers"
                :key="m.id"
                :value="m.id"
                :label="m.name" /></el-select></el-form-item
          ><el-form-item label="包装单位"
            ><el-select
              v-model="form.packagingUnitId"
              clearable
              :disabled="!!editId"
              ><el-option
                v-for="u in options.units.filter(
                  (u: any) => u.id !== form.baseUnitId,
                )"
                :key="u.id"
                :value="u.id"
                :label="u.name" /></el-select></el-form-item
          ><el-form-item label="每包装基础单位数量"
            ><el-input
              v-model="form.packagingFactor"
              :disabled="!!editId" /></el-form-item
          ><el-form-item label="剂型"
            ><el-select v-model="form.dosageForm"
              ><el-option
                v-for="v in [
                  '片剂',
                  '胶囊',
                  '针剂',
                  '输液',
                  '合剂',
                  '中药饮片',
                  '颗粒',
                ]"
                :key="v"
                :value="v"
                :label="v" /></el-select></el-form-item
          ><el-form-item label="药品类别"
            ><el-input v-model="form.category" /></el-form-item
          ><el-form-item label="医保类别"
            ><el-select v-model="form.insuranceCategory"
              ><el-option
                v-for="v in ['A', 'B', 'SELF_PAY']"
                :key="v"
                :value="v"
                :label="
                  { A: '甲类', B: '乙类', SELF_PAY: '自费' }[v]
                " /></el-select></el-form-item
          ><el-form-item label="医保编码"
            ><el-input v-model="form.insuranceCode" /></el-form-item
          ><el-form-item label="控制级别"
            ><el-select v-model="form.controlLevel"
              ><el-option
                v-for="(v, k) in controls"
                :key="k"
                :label="v"
                :value="k" /></el-select></el-form-item
          ><el-form-item label="零售价（元/基础单位）"
            ><el-input
              v-model="form.retailPrice"
              :disabled="!!editId" /></el-form-item
          ><el-form-item label="数量小数位"
            ><el-input-number v-model="form.quantityScale" :min="0" :max="6"
          /></el-form-item>
        </div>
        <el-form-item label="别名"
          ><el-input
            v-model="aliasText"
            placeholder="多个别名以逗号分隔" /></el-form-item
        ><el-form-item label="药品标志"
          ><el-checkbox v-model="form.prescriptionRequired">处方药</el-checkbox
          ><el-checkbox v-model="form.imported">进口</el-checkbox
          ><el-checkbox v-model="form.inHouse">院内制剂</el-checkbox
          ><el-checkbox v-model="form.selfPay">自费</el-checkbox
          ><el-checkbox v-model="form.valuable">贵重</el-checkbox></el-form-item
        ></el-form
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      ></el-drawer
    >
  </div>
</template>
