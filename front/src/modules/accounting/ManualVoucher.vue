<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { get, post } from "../../api/client";
import { Plus } from "lucide-vue-next";
const emit = defineEmits<{ saved: [] }>(),
  accounts = ref<any[]>([]),
  open = ref(false),
  saving = ref(false),
  form = reactive({
    description: "",
    entries: [
      { accountId: "5", debit: "0", credit: "0" },
      { accountId: "7", debit: "0", credit: "0" },
    ],
  });
onMounted(async () => {
  accounts.value = await get("/accounting/accounts");
});
async function save() {
  saving.value = true;
  try {
    await post("/accounting/vouchers", form, crypto.randomUUID());
    open.value = false;
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <el-button type="primary" @click="open = true"
    ><Plus :size="16" />手工凭证</el-button
  ><el-dialog v-model="open" title="手工凭证草稿" width="750px"
    ><el-form label-position="top"
      ><el-form-item label="摘要"
        ><el-input v-model="form.description" /></el-form-item></el-form
    ><el-table :data="form.entries"
      ><el-table-column label="科目"
        ><template #default="{ row }"
          ><el-select v-model="row.accountId"
            ><el-option
              v-for="a in accounts"
              :key="a.id"
              :value="a.id"
              :label="a.name" /></el-select></template></el-table-column
      ><el-table-column label="借方金额"
        ><template #default="{ row }"
          ><el-input v-model="row.debit" /></template></el-table-column
      ><el-table-column label="贷方金额"
        ><template #default="{ row }"
          ><el-input v-model="row.credit" /></template></el-table-column
      ><el-table-column width="70"
        ><template #default="{ $index }"
          ><el-button link type="danger" @click="form.entries.splice($index, 1)"
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-button
      style="margin-top: 12px"
      @click="form.entries.push({ accountId: '5', debit: '0', credit: '0' })"
      >增加分录</el-button
    ><template #footer
      ><el-button @click="open = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存待过账</el-button
      ></template
    ></el-dialog
  >
</template>
