<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import { Plus, Search, Wallet, ClipboardPlus } from "lucide-vue-next";
import { ElMessage, ElMessageBox } from "element-plus";
import { get, post, put } from "../../api/client";
import { useAuth } from "../../stores/auth";
const auth = useAuth(),
  route = useRoute(),
  admissions = computed(() => route.path.includes("admissions")),
  rows = ref<any[]>([]),
  patients = ref<any[]>([]),
  departments = ref<any[]>([]),
  locations = ref<any[]>([]),
  options = ref<any>({ diagnoses: [] }),
  loading = ref(false),
  open = ref(false),
  encounterOpen = ref(false),
  saving = ref(false),
  keyword = ref("");
const editId = ref("");
const form = reactive({
    name: "",
    sex: "MALE",
    birthDate: "1990-01-01",
    phone: "",
    weightKg: "",
    allergyNotes: "",
  }),
  encounter = reactive({
    patientId: "",
    type: "OUTPATIENT",
    departmentId: "6",
    wardLocationId: null as string | null,
    bedNo: "",
    diagnosisIds: [] as string[],
  });
async function load() {
  loading.value = true;
  try {
    rows.value = await get(
      admissions.value ? "/clinical/encounters" : "/clinical/patients",
      admissions.value ? { type: "INPATIENT" } : { keyword: keyword.value },
    );
    patients.value = admissions.value
      ? await get("/clinical/patients")
      : rows.value;
  } finally {
    loading.value = false;
  }
}
async function save() {
  if (!form.name.trim()) {
    ElMessage.warning("请填写姓名");
    return;
  }
  saving.value = true;
  try {
    const data = {
      ...form,
      weightKg: form.weightKg || null,
    };
    if (editId.value) await put("/clinical/patients/" + editId.value, data);
    else await post("/clinical/patients", data);
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
function editPatient(row: any) {
  editId.value = row.id;
  Object.assign(form, {
    name: row.name,
    sex: row.sex,
    birthDate: row.birth_date,
    phone: row.phone ?? "",
    weightKg: row.weight_kg ?? "",
    allergyNotes: row.allergy_notes ?? "",
  });
  open.value = true;
}
function newEncounter(row?: any) {
  encounter.patientId = row?.id ?? patients.value[0]?.id ?? "";
  encounter.type = admissions.value ? "INPATIENT" : "OUTPATIENT";
  encounter.departmentId = auth.user?.roles.includes("DOCTOR") ? "6" : "6";
  encounter.wardLocationId = null;
  encounter.bedNo = "";
  encounter.diagnosisIds = [];
  encounterOpen.value = true;
}
async function saveEncounter() {
  saving.value = true;
  try {
    await post("/clinical/encounters", encounter);
    encounterOpen.value = false;
    ElMessage.success("就诊登记已保存");
    await load();
  } finally {
    saving.value = false;
  }
}
async function deposit(row: any) {
  const r = await ElMessageBox.prompt("请输入押金金额（元）", "住院押金", {
    inputPattern: /^\d+(\.\d{1,2})?$/,
    inputErrorMessage: "请输入有效金额",
  });
  await post(
    `/clinical/encounters/${row.id}/deposits`,
    { amount: r.value },
    crypto.randomUUID(),
  );
  await load();
}
onMounted(async () => {
  await load();
  departments.value = await get("/system/departments");
  locations.value = await get("/master/transfer-locations");
  options.value = await get("/master/options");
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <h1>{{ admissions ? "住院登记与押金" : "患者档案" }}</h1>
      <el-button
        v-if="auth.can('clinical:write')"
        type="primary"
        @click="admissions ? newEncounter() : ((editId = ''), (open = true))"
        ><Plus :size="16" />{{
          admissions ? "住院登记" : "新增患者"
        }}</el-button
      >
    </div>
    <div class="filters">
      <el-input
        v-model="keyword"
        placeholder="姓名或患者编号"
        style="width: 280px"
        clearable
        @keyup.enter="load"
      /><el-button @click="load"><Search :size="16" /> 查询</el-button>
    </div>
    <div class="table-region" style="margin-top: 16px">
      <el-table :data="rows" v-loading="loading"
        ><template v-if="admissions"
          ><el-table-column
            prop="number"
            label="住院号"
            min-width="190"
          /><el-table-column prop="patientName" label="患者" /><el-table-column
            prop="department"
            label="科室"
          /><el-table-column prop="bed_no" label="床号" /><el-table-column
            prop="balance"
            label="押金余额"
            align="right"
          /><el-table-column prop="status" label="状态" /><el-table-column
            label="操作"
            width="140"
            ><template #default="{ row }"
              ><el-button
                v-if="auth.can('clinical:payment')"
                link
                type="primary"
                @click="deposit(row)"
                ><Wallet :size="14" /> 交押金</el-button
              ></template
            ></el-table-column
          ></template
        ><template v-else
          ><el-table-column
            prop="patient_no"
            label="患者编号"
            min-width="190"
          /><el-table-column
            prop="name"
            label="姓名"
            min-width="100"
          /><el-table-column label="性别" width="80"
            ><template #default="{ row }">{{
              { MALE: "男", FEMALE: "女", OTHER: "其他" }[row.sex as "MALE"]
            }}</template></el-table-column
          ><el-table-column
            prop="birth_date"
            label="出生日期"
            width="120"
          /><el-table-column
            prop="weight_kg"
            label="体重(kg)"
            width="100"
          /><el-table-column
            prop="phone"
            label="联系电话"
            min-width="140"
          /><el-table-column
            prop="allergy_notes"
            label="过敏史"
            min-width="180"
          /><el-table-column
            v-if="auth.can('clinical:write')"
            label="操作"
            width="180"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="newEncounter(row)"
                ><ClipboardPlus :size="14" />就诊登记</el-button
              ><el-button link type="primary" @click="editPatient(row)"
                >修改资料</el-button
              ></template
            ></el-table-column
          ></template
        ></el-table
      >
    </div>
    <el-dialog v-model="open" title="新增患者" width="580px"
      ><el-form label-position="top"
        ><div class="form-grid">
          <el-form-item label="姓名" required
            ><el-input v-model="form.name" maxlength="100" /></el-form-item
          ><el-form-item label="性别"
            ><el-select v-model="form.sex"
              ><el-option label="男" value="MALE" /><el-option
                label="女"
                value="FEMALE" /><el-option
                label="其他"
                value="OTHER" /></el-select></el-form-item
          ><el-form-item label="出生日期" required
            ><el-date-picker
              v-model="form.birthDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%" /></el-form-item
          ><el-form-item label="体重（kg）"
            ><el-input v-model="form.weightKg" /></el-form-item
          ><el-form-item label="联系电话"
            ><el-input v-model="form.phone"
          /></el-form-item>
        </div>
        <el-form-item label="过敏史说明"
          ><el-input
            v-model="form.allergyNotes"
            type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="encounterOpen"
      :title="admissions ? '住院登记' : '门诊就诊登记'"
      width="580px"
      ><el-form label-position="top"
        ><div class="form-grid">
          <el-form-item label="患者" required
            ><el-select v-model="encounter.patientId" filterable
              ><el-option
                v-for="p in patients"
                :key="p.id"
                :value="p.id"
                :label="
                  p.name + ' · ' + p.patient_no
                " /></el-select></el-form-item
          ><el-form-item label="科室" required
            ><el-select v-model="encounter.departmentId"
              ><el-option
                v-for="d in departments"
                :key="d.id"
                :value="d.id"
                :label="d.name" /></el-select></el-form-item
          ><template v-if="admissions"
            ><el-form-item label="病区" required
              ><el-select v-model="encounter.wardLocationId"
                ><el-option
                  v-for="l in locations.filter((l) => l.type === 'WARD')"
                  :key="l.id"
                  :value="l.id"
                  :label="l.name" /></el-select></el-form-item
            ><el-form-item label="床号"
              ><el-input v-model="encounter.bedNo" /></el-form-item
          ></template>
        </div>
        <el-form-item label="诊断"
          ><el-select
            v-model="encounter.diagnosisIds"
            multiple
            style="width: 100%"
            ><el-option
              v-for="d in options.diagnoses"
              :key="d.id"
              :value="d.id"
              :label="d.name" /></el-select></el-form-item></el-form
      ><template #footer
        ><el-button @click="encounterOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveEncounter"
          >保存登记</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
