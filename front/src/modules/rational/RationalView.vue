<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { Plus, ShieldCheck, BookOpen } from "lucide-vue-next";
import { get, post } from "../../api/client";
import { useAuth } from "../../stores/auth";
const auth = useAuth(),
  rows = ref<any[]>([]),
  knowledge = ref<any[]>([]),
  drugs = ref<any[]>([]),
  options = ref<any>({ diagnoses: [] }),
  patients = ref<any[]>([]),
  open = ref(false),
  knowledgeOpen = ref(false),
  allergyOpen = ref(false),
  loading = ref(false),
  saving = ref(false),
  tab = ref("rules");
const form = reactive({
    type: "INTERACTION",
    name: "",
    itemId: "",
    peerItemId: null as string | null,
    diagnosisId: null as string | null,
    minDose: null as string | null,
    maxDose: null as string | null,
    doseUnit: "mg",
    severity: "WARNING",
    message: "",
    sourceNote: "课程演示",
    minAge: undefined as number | undefined,
    maxAge: undefined as number | undefined,
    doseBasis: "DAILY",
    perKg: false,
    route: null as string | null,
  }),
  article = reactive({
    itemId: "",
    title: "",
    content: "",
    sourceNote: "课程演示",
  }),
  allergy = reactive({ patientId: "", itemId: "", reaction: "" });
const types: Record<string, string> = {
  INTERACTION: "相互作用",
  DOSE: "剂量范围",
  CONTRAINDICATION: "禁忌症",
  INDICATION: "适应症",
};
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/rational/rules");
    knowledge.value = await get("/rational/knowledge");
  } finally {
    loading.value = false;
  }
}
async function save(kind: string) {
  saving.value = true;
  try {
    await post(
      "/rational/" + kind,
      kind === "rules" ? form : kind === "knowledge" ? article : allergy,
    );
    open.value = false;
    knowledgeOpen.value = false;
    allergyOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
onMounted(async () => {
  await load();
  drugs.value = (await get("/master/drugs", { pageSize: 100 })).records;
  options.value = await get("/master/options");
  if (auth.can("clinical:read"))
    patients.value = await get("/clinical/patients");
});
</script>
<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>合理用药规则与知识</h1>
        <p class="muted">规则来源：课程演示资料</p>
      </div>
      <div>
        <el-button v-if="auth.can('clinical:write')" @click="allergyOpen = true"
          >登记药物过敏</el-button
        ><el-button
          v-if="auth.can('rational:write')"
          type="primary"
          @click="tab === 'rules' ? (open = true) : (knowledgeOpen = true)"
          ><Plus :size="16" />新增{{
            tab === "rules" ? "规则" : "知识"
          }}</el-button
        >
      </div>
    </div>
    <el-tabs v-model="tab"
      ><el-tab-pane label="审查规则" name="rules" /><el-tab-pane
        label="药品知识"
        name="knowledge"
    /></el-tabs>
    <div v-if="tab === 'rules'" class="table-region">
      <el-table :data="rows" v-loading="loading"
        ><el-table-column label="类型" width="120"
          ><template #default="{ row }">{{
            types[row.type]
          }}</template></el-table-column
        ><el-table-column
          prop="name"
          label="规则名称"
          min-width="160" /><el-table-column
          prop="drugName"
          label="药品"
          min-width="160" /><el-table-column
          prop="peerName"
          label="相关药品"
          min-width="140" /><el-table-column label="严重度" width="100"
          ><template #default="{ row }"
            ><el-tag :type="row.severity === 'BLOCK' ? 'danger' : 'warning'">{{
              row.severity === "BLOCK" ? "阻断" : "警告"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column
          prop="message"
          label="审查说明"
          min-width="240" /><el-table-column
          prop="revision"
          label="版本"
          width="70"
      /></el-table>
    </div>
    <div v-else>
      <article v-for="k in knowledge" :key="k.id" class="knowledge">
        <div class="section-title">
          <h2><BookOpen :size="16" /> {{ k.title }}</h2>
          <el-tag type="info">{{ k.drugName }}</el-tag>
        </div>
        <p>{{ k.content }}</p>
        <span class="muted">{{ k.source_note }} · V{{ k.revision }}</span>
      </article>
      <div v-if="!knowledge.length" class="empty">暂无药品知识</div>
    </div>
    <el-dialog v-model="open" title="新增审查规则" width="680px"
      ><el-form label-position="top"
        ><div class="form-grid">
          <el-form-item label="规则类型"
            ><el-select v-model="form.type"
              ><el-option
                v-for="(label, type) in types"
                :key="type"
                :value="type"
                :label="label" /></el-select></el-form-item
          ><el-form-item label="规则名称"
            ><el-input v-model="form.name" /></el-form-item
          ><el-form-item label="药品"
            ><el-select v-model="form.itemId" filterable
              ><el-option
                v-for="d in drugs"
                :key="d.id"
                :value="d.id"
                :label="d.name" /></el-select></el-form-item
          ><el-form-item v-if="form.type === 'INTERACTION'" label="相关药品"
            ><el-select v-model="form.peerItemId" filterable
              ><el-option
                v-for="d in drugs"
                :key="d.id"
                :value="d.id"
                :label="d.name" /></el-select></el-form-item
          ><el-form-item
            v-if="['CONTRAINDICATION', 'INDICATION'].includes(form.type)"
            label="诊断"
            ><el-select v-model="form.diagnosisId"
              ><el-option
                v-for="d in options.diagnoses"
                :key="d.id"
                :value="d.id"
                :label="d.name" /></el-select></el-form-item
          ><template v-if="form.type === 'DOSE'"
            ><el-form-item label="每日最低剂量"
              ><el-input v-model="form.minDose" /></el-form-item
            ><el-form-item label="每日最高剂量"
              ><el-input v-model="form.maxDose" /></el-form-item
            ><el-form-item label="剂量单位"
              ><el-input v-model="form.doseUnit" /></el-form-item></template
          ><el-form-item label="严重度"
            ><el-select v-model="form.severity"
              ><el-option label="普通警告" value="WARNING" /><el-option
                label="严重阻断"
                value="BLOCK" /></el-select
          ></el-form-item>
        </div>
        <div v-if="form.type === 'DOSE'" class="form-grid">
          <el-form-item label="最低年龄"
            ><el-input-number
              v-model="form.minAge"
              :min="0"
              :max="150" /></el-form-item
          ><el-form-item label="最高年龄"
            ><el-input-number
              v-model="form.maxAge"
              :min="0"
              :max="150" /></el-form-item
          ><el-form-item label="剂量口径"
            ><el-select v-model="form.doseBasis"
              ><el-option label="日剂量" value="DAILY" /><el-option
                label="单次剂量"
                value="SINGLE" /></el-select></el-form-item
          ><el-form-item label="按体重检查"
            ><el-switch v-model="form.perKg"
          /></el-form-item>
        </div>
        <el-form-item label="警告说明"
          ><el-input v-model="form.message" type="textarea" /></el-form-item
        ><el-form-item label="资料来源"
          ><el-input v-model="form.sourceNote" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save('rules')"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="knowledgeOpen" title="新增药品知识" width="620px"
      ><el-form label-position="top"
        ><el-form-item label="药品"
          ><el-select v-model="article.itemId" filterable
            ><el-option
              v-for="d in drugs"
              :key="d.id"
              :value="d.id"
              :label="d.name" /></el-select></el-form-item
        ><el-form-item label="标题"
          ><el-input v-model="article.title" /></el-form-item
        ><el-form-item label="用法、用量及注意事项"
          ><el-input
            v-model="article.content"
            type="textarea"
            :rows="6" /></el-form-item
        ><el-form-item label="资料来源"
          ><el-input v-model="article.sourceNote" /></el-form-item></el-form
      ><template #footer
        ><el-button type="primary" :loading="saving" @click="save('knowledge')"
          >保存</el-button
        ></template
      ></el-dialog
    ><el-dialog v-model="allergyOpen" title="登记药物过敏史" width="520px"
      ><el-form label-position="top"
        ><el-form-item label="患者"
          ><el-select v-model="allergy.patientId" filterable
            ><el-option
              v-for="p in patients"
              :key="p.id"
              :value="p.id"
              :label="p.name" /></el-select></el-form-item
        ><el-form-item label="过敏药品"
          ><el-select v-model="allergy.itemId" filterable
            ><el-option
              v-for="d in drugs"
              :key="d.id"
              :value="d.id"
              :label="d.name" /></el-select></el-form-item
        ><el-form-item label="过敏反应"
          ><el-input v-model="allergy.reaction" /></el-form-item></el-form
      ><template #footer
        ><el-button type="primary" :loading="saving" @click="save('allergies')"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.knowledge {
  padding: 20px 0;
  border-bottom: 1px solid #dde1e6;
}
.knowledge h2 {
  display: flex;
  align-items: center;
  gap: 8px;
}
.knowledge p {
  font-size: 14px;
  line-height: 1.9;
  white-space: pre-wrap;
}
</style>
