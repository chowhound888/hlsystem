<script setup lang="ts">
import { ref, onMounted } from "vue";
import { get } from "../../api/client";
import { useAuth } from "../../stores/auth";
import {
  ArrowUpRight,
  PackageCheck,
  ClipboardList,
  ShieldAlert,
  CalendarDays,
} from "lucide-vue-next";
const auth = useAuth(),
  loading = ref(false),
  data = ref<any>(null),
  error = ref(false);
async function load() {
  loading.value = true;
  error.value = false;
  try {
    data.value = await get("/workbench", { locationId: auth.locationId });
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div>
        <h1>业务工作台</h1>
        <p class="muted">
          {{ auth.user?.displayName }} ·
          {{ auth.user?.locations.find((l) => l.id === auth.locationId)?.name }}
        </p>
      </div>
      <span class="muted"
        ><CalendarDays :size="14" />
        {{ new Date().toLocaleDateString("zh-CN") }}</span
      >
    </div>
    <div v-if="error" class="error-region">
      <p>工作台数据加载失败</p>
      <el-button @click="load">重试</el-button>
    </div>
    <template v-else-if="data"
      ><div class="summary-band">
        <div class="summary-item">
          <span class="muted">药品品种</span>
          <div class="summary-value">{{ data.drugCount }}</div>
        </div>
        <div class="summary-item">
          <span class="muted">库存成本（元）</span>
          <div class="summary-value">{{ data.inventoryCost }}</div>
        </div>
        <div class="summary-item">
          <span class="muted">待处理单据</span>
          <div class="summary-value">{{ data.pendingCount }}</div>
        </div>
        <div class="summary-item">
          <span class="muted">库存预警</span>
          <div class="summary-value" style="color: #b45309">
            {{ data.alertCount }}
          </div>
        </div>
      </div>
      <div class="split">
        <section class="section">
          <div class="section-title">
            <h2>最近业务单据</h2>
            <router-link to="/inventory/documents" class="muted"
              >查看全部 <ArrowUpRight :size="14"
            /></router-link>
          </div>
          <div class="table-region">
            <el-table :data="data.recentDocuments"
              ><el-table-column
                prop="number"
                label="单据号"
                min-width="170" /><el-table-column
                prop="type"
                label="类型" /><el-table-column
                prop="status"
                label="状态" /><el-table-column
                prop="business_date"
                label="日期"
                width="110"
            /></el-table>
          </div>
        </section>
        <section class="section">
          <div class="section-title">
            <h2>库存预警</h2>
            <ShieldAlert :size="18" color="#b45309" />
          </div>
          <div v-if="!data.alerts.length" class="empty">暂无库存预警</div>
          <div v-for="a in data.alerts" :key="a.id" class="alert-line">
            <div>
              <strong>{{ a.name }}</strong>
              <p class="muted">{{ a.batch_no }} · {{ a.expires_on }}</p>
            </div>
            <el-tag type="warning">{{ a.reason }}</el-tag>
          </div>
        </section>
      </div>
      <section class="section">
        <h2>常用业务</h2>
        <div class="quick-links">
          <router-link v-if="auth.can('inventory:read')" to="/inventory/stocks"
            ><PackageCheck :size="20" />库存查询<ArrowUpRight
              :size="16" /></router-link
          ><router-link
            v-if="auth.can('outpatient:read')"
            to="/outpatient/prescriptions"
            ><ClipboardList :size="20" />处方发药<ArrowUpRight
              :size="16" /></router-link
          ><router-link v-if="auth.can('master:read')" to="/master/drugs"
            ><PackageCheck :size="20" />药品字典<ArrowUpRight :size="16"
          /></router-link>
        </div></section
    ></template>
  </div>
</template>
<style scoped>
.alert-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #e5e7eb;
}
.alert-line strong {
  font-size: 14px;
}
.alert-line p {
  margin: 5px 0 0;
}
.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 20px;
}
.quick-links a {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #2563eb;
  padding: 12px 0;
}
</style>
