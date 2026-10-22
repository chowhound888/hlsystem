<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from "vue";
import { get } from "../../api/client";
import { useAuth } from "../../stores/auth";
import { formatNumber } from "../../composables/useCountUp";
import * as echarts from "echarts";
import {
  ArrowUpRight, PackageCheck, ClipboardList, ShieldAlert, CalendarDays,
  Pill, DollarSign, FileText, AlertTriangle, ChevronLeft, ChevronRight,
} from "lucide-vue-next";

const auth = useAuth();
const loading = ref(false);
const data = ref<any>(null);
const error = ref(false);
const mounted = ref(false);

// 数字滚动
const drugCount = ref(0);
const inventoryCost = ref(0);
const pendingCount = ref(0);
const alertCount = ref(0);

// 预警分页（不用 TransitionGroup，直接切换避免跳动）
const alertPage = ref(0);
const alertPageSize = 5;
const alertPages = computed(() => Math.ceil((data.value?.alerts?.length || 0) / alertPageSize));
const visibleAlerts = computed(() => {
  const alerts = data.value?.alerts || [];
  const start = alertPage.value * alertPageSize;
  return alerts.slice(start, start + alertPageSize);
});
const alertTransitioning = ref(false);
function changeAlertPage(dir: number) {
  const next = alertPage.value + dir;
  if (next < 0 || next >= alertPages.value) return;
  alertTransitioning.value = true;
  setTimeout(() => {
    alertPage.value = next;
    alertTransitioning.value = false;
  }, 150);
}

// 最近单据分页
const docPage = ref(0);
const docPageSize = 5;
const docPages = computed(() => Math.ceil((data.value?.recentDocuments?.length || 0) / docPageSize));
const visibleDocs = computed(() => {
  const docs = data.value?.recentDocuments || [];
  const start = docPage.value * docPageSize;
  return docs.slice(start, start + docPageSize);
});
const docTransitioning = ref(false);
function changeDocPage(dir: number) {
  const next = docPage.value + dir;
  if (next < 0 || next >= docPages.value) return;
  docTransitioning.value = true;
  setTimeout(() => {
    docPage.value = next;
    docTransitioning.value = false;
  }, 150);
}

// 图表
const categoryChartRef = ref<HTMLDivElement>();
const trendChartRef = ref<HTMLDivElement>();
const deptChartRef = ref<HTMLDivElement>();
const expiryChartRef = ref<HTMLDivElement>();
let charts: echarts.ECharts[] = [];

function animateNumbers() {
  if (!data.value) return;
  const dur = 1500;
  const start = performance.now();
  const targets = {
    drug: Number(data.value.drugCount) || 0,
    cost: Number(data.value.inventoryCost) || 0,
    pending: Number(data.value.pendingCount) || 0,
    alert: Number(data.value.alertCount) || 0,
  };
  function tick(now: number) {
    const p = Math.min((now - start) / dur, 1);
    const e = p === 1 ? 1 : 1 - Math.pow(2, -10 * p);
    drugCount.value = targets.drug * e;
    inventoryCost.value = targets.cost * e;
    pendingCount.value = targets.pending * e;
    alertCount.value = targets.alert * e;
    if (p < 1) requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
}

function initCharts() {
  if (!data.value) return;
  charts.forEach(c => c.dispose());
  charts = [];

  const colors = ["#0d9488", "#2563eb", "#7c3aed", "#d97706", "#dc2626", "#059669", "#6366f1", "#ec4899"];
  const hasData = (arr: any) => Array.isArray(arr) && arr.length > 0;

  // 1. 库存分类占比 - 环形图
  if (categoryChartRef.value && hasData(data.value.categoryDistribution)) {
    try {
      const chart = echarts.init(categoryChartRef.value);
      charts.push(chart);
      chart.setOption({
        color: colors,
        tooltip: { trigger: "item", formatter: "{b}: ¥{c} ({d}%)" },
        series: [{
          type: "pie", radius: ["42%", "70%"], center: ["50%", "50%"],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 6, borderColor: "#fff", borderWidth: 2 },
          label: { show: false },
          emphasis: {
            label: { show: true, fontSize: 13, fontWeight: "bold" },
            itemStyle: { shadowBlur: 10, shadowColor: "rgba(0,0,0,0.15)" },
          },
          data: data.value.categoryDistribution.map((d: any) => ({ name: d.name || "未分类", value: Number(d.value) })),
          animationType: "scale",
          animationEasing: "elasticOut",
          animationDelay: (idx: number) => idx * 80,
        }],
      });
    } catch (e) { console.warn("Category chart error:", e); }
  }

  // 2. 近7天出入库趋势 - 折线图
  if (trendChartRef.value) {
    try {
      const chart = echarts.init(trendChartRef.value);
      charts.push(chart);
      const trend = data.value.weeklyTrend || [];
      const inboundMap: Record<string, number> = {};
      const outboundMap: Record<string, number> = {};
      trend.forEach((t: any) => {
        inboundMap[t.date] = Number(t.inbound) || 0;
        outboundMap[t.date] = Number(t.outbound) || 0;
      });
      const days: string[] = [];
      const inData: number[] = [];
      const outData: number[] = [];
      for (let i = 6; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        const key = d.toISOString().slice(0, 10);
        days.push(key.slice(5));
        inData.push(inboundMap[key] || 0);
        outData.push(outboundMap[key] || 0);
      }
      chart.setOption({
        color: ["#0d9488", "#f59e0b"],
        tooltip: { trigger: "axis" },
        legend: { data: ["入库", "出库"], bottom: 0, textStyle: { fontSize: 11 } },
        grid: { top: 10, right: 10, bottom: 30, left: 40 },
        xAxis: { type: "category", data: days, axisLabel: { fontSize: 11 }, axisLine: { lineStyle: { color: "#e5e7eb" } } },
        yAxis: { type: "value", axisLabel: { fontSize: 11 }, splitLine: { lineStyle: { color: "#f3f4f6" } } },
        series: [
          { name: "入库", type: "line", smooth: true, symbol: "circle", symbolSize: 6, areaStyle: { opacity: 0.08 }, data: inData, animationDuration: 1500 },
          { name: "出库", type: "line", smooth: true, symbol: "circle", symbolSize: 6, areaStyle: { opacity: 0.08 }, data: outData, animationDuration: 1500 },
        ],
      });
    } catch (e) { console.warn("Trend chart error:", e); }
  }

  // 3. 科室消耗排行 - 横向柱状图
  if (deptChartRef.value && hasData(data.value.departmentConsumption)) {
    try {
      const chart = echarts.init(deptChartRef.value);
      charts.push(chart);
      const deptData = [...data.value.departmentConsumption].reverse();
      chart.setOption({
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: (p: any) => `${p[0].name}: ¥${Number(p[0].value).toLocaleString()}` },
        grid: { top: 5, right: 20, bottom: 5, left: 80 },
        xAxis: { type: "value", show: false },
        yAxis: { type: "category", data: deptData.map((d: any) => d.name), axisLabel: { fontSize: 11, width: 70, overflow: "truncate" }, axisLine: { show: false }, axisTick: { show: false } },
        series: [{
          type: "bar", barWidth: 14,
          itemStyle: { borderRadius: [0, 8, 8, 0], color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: "#2563eb" }, { offset: 1, color: "#60a5fa" }]) },
          data: deptData.map((d: any) => Number(d.value)),
          animationDuration: 1200,
          animationDelay: (idx: number) => idx * 100,
        }],
      });
    } catch (e) { console.warn("Dept chart error:", e); }
  }

  // 4. 效期分布 - 柱状图
  if (expiryChartRef.value && hasData(data.value.expiryDistribution)) {
    try {
      const chart = echarts.init(expiryChartRef.value);
      charts.push(chart);
      const expColors: Record<string, string> = { "已过期": "#dc2626", "30天内": "#f59e0b", "90天内": "#d97706", "180天内": "#2563eb", "180天以上": "#059669" };
      chart.setOption({
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
        grid: { top: 10, right: 10, bottom: 25, left: 35 },
        xAxis: { type: "category", data: data.value.expiryDistribution.map((d: any) => d.name), axisLabel: { fontSize: 11 }, axisLine: { lineStyle: { color: "#e5e7eb" } } },
        yAxis: { type: "value", axisLabel: { fontSize: 11 }, splitLine: { lineStyle: { color: "#f3f4f6" } } },
        series: [{
          type: "bar", barWidth: 28,
          itemStyle: { borderRadius: [6, 6, 0, 0], color: (p: any) => expColors[p.name] || "#6b7280" },
          data: data.value.expiryDistribution.map((d: any) => Number(d.value)),
          animationDuration: 1200,
          animationEasing: "elasticOut",
          animationDelay: (idx: number) => idx * 120,
        }],
      });
    } catch (e) { console.warn("Expiry chart error:", e); }
  }
}

async function load() {
  loading.value = true;
  error.value = false;
  try {
    data.value = await get("/workbench", { locationId: auth.locationId });
    alertPage.value = 0;
    setTimeout(animateNumbers, 300);
    // 等 DOM 渲染完成后再初始化图表
    nextTick(() => {
      setTimeout(initCharts, 500);
    });
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

function handleResize() { charts.forEach(c => c.resize()); }

onMounted(() => {
  load();
  setTimeout(() => { mounted.value = true; }, 50);
  window.addEventListener("resize", handleResize);
});

onUnmounted(() => {
  charts.forEach(c => c.dispose());
  window.removeEventListener("resize", handleResize);
});

const quickLinks = computed(() => [
  { icon: PackageCheck, label: "库存查询", path: "/inventory/stocks", perm: "inventory:read", color: "#0d9488" },
  { icon: ClipboardList, label: "处方发药", path: "/outpatient/prescriptions", perm: "outpatient:read", color: "#2563eb" },
  { icon: Pill, label: "药品字典", path: "/master/drugs", perm: "master:read", color: "#7c3aed" },
  { icon: FileText, label: "库存单据", path: "/inventory/documents", perm: "inventory:read", color: "#d97706" },
]);
</script>

<template>
  <div class="page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-header page-header-animate">
      <div>
        <h1>业务工作台</h1>
        <p class="muted">{{ auth.user?.displayName }} · {{ auth.user?.locations.find((l: any) => l.id === auth.locationId)?.name }}</p>
      </div>
      <span class="muted date-badge"><CalendarDays :size="14" /> {{ new Date().toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "long" }) }}</span>
    </div>

    <div v-if="error" class="error-region"><p>工作台数据加载失败</p><el-button @click="load">重试</el-button></div>

    <template v-else-if="data">
      <!-- 统计卡片 -->
      <div class="stats-grid">
        <div v-for="(card, i) in [
          { icon: Pill, label: '药品品种', value: drugCount, decimals: 0, color: '#0d9488', bg: '#f0fdfa' },
          { icon: DollarSign, label: '库存成本（元）', value: inventoryCost, decimals: 2, color: '#2563eb', bg: '#eff6ff' },
          { icon: FileText, label: '待处理单据', value: pendingCount, decimals: 0, color: '#d97706', bg: '#fffbeb' },
          { icon: AlertTriangle, label: '库存预警', value: alertCount, decimals: 0, color: '#dc2626', bg: '#fef2f2' },
        ]" :key="i" class="stat-card" :class="{ 'stat-card-visible': mounted }" :style="{ '--delay': `${i * 0.1}s`, '--accent': card.color, '--accent-bg': card.bg }">
          <div class="stat-icon"><component :is="card.icon" :size="22" /></div>
          <div class="stat-info">
            <span class="stat-label">{{ card.label }}</span>
            <span class="stat-value">{{ formatNumber(card.value, card.decimals) }}</span>
          </div>
          <div class="stat-glow"></div>
        </div>
      </div>

      <!-- 图表区 2x2 -->
      <div class="charts-grid section-animate" style="--delay: 0.35s">
        <div class="chart-card">
          <h3>📊 库存分类占比</h3>
          <div ref="categoryChartRef" class="chart-body"></div>
          <div v-if="!data.categoryDistribution?.length" class="chart-empty">暂无数据</div>
        </div>
        <div class="chart-card">
          <h3>📈 近7天出入库趋势</h3>
          <div ref="trendChartRef" class="chart-body"></div>
        </div>
        <div class="chart-card">
          <h3>🏥 科室消耗排行（近30天）</h3>
          <div ref="deptChartRef" class="chart-body"></div>
          <div v-if="!data.departmentConsumption?.length" class="chart-empty">暂无数据</div>
        </div>
        <div class="chart-card">
          <h3>⏰ 效期分布</h3>
          <div ref="expiryChartRef" class="chart-body"></div>
          <div v-if="!data.expiryDistribution?.length" class="chart-empty">暂无数据</div>
        </div>
      </div>

      <!-- 下方并排：最近单据 + 库存预警（等高） -->
      <div class="bottom-split section-animate" style="--delay: 0.5s">
        <!-- 最近单据 -->
        <div class="panel-card">
          <div class="panel-header">
            <h2>最近业务单据</h2>
            <router-link to="/inventory/documents" class="view-all">查看全部 <ArrowUpRight :size="14" /></router-link>
          </div>
          <div class="panel-body">
            <div class="doc-list" :class="{ 'alert-fade': docTransitioning }">
              <div v-for="doc in visibleDocs" :key="doc.id || doc.number" class="doc-line">
                <div class="doc-info">
                  <span class="doc-number">{{ doc.number }}</span>
                  <span class="doc-meta">
                    <span class="doc-type">{{ doc.type }}</span>
                    <span class="status-dot" :class="'status-' + doc.status?.toLowerCase()"></span>
                    <span class="doc-status">{{ doc.status }}</span>
                  </span>
                </div>
                <span class="doc-date">{{ doc.business_date }}</span>
              </div>
              <div v-if="!visibleDocs.length" class="empty-state-mini"><p>暂无单据</p></div>
            </div>
            <div class="alert-pagination" v-if="docPages > 1">
              <button class="page-btn" :disabled="docPage === 0" @click="changeDocPage(-1)"><ChevronLeft :size="14" /></button>
              <span class="page-info">{{ docPage + 1 }} / {{ docPages }}</span>
              <button class="page-btn" :disabled="docPage >= docPages - 1" @click="changeDocPage(1)"><ChevronRight :size="14" /></button>
            </div>
          </div>
        </div>

        <!-- 库存预警 -->
        <div class="panel-card">
          <div class="panel-header">
            <h2>库存预警 <span v-if="data.alerts.length" class="alert-badge pulse-badge">{{ data.alerts.length }}</span></h2>
            <ShieldAlert :size="18" color="#dc2626" class="alert-icon-pulse" />
          </div>
          <div class="panel-body">
            <div v-if="!data.alerts.length" class="empty-state-mini">
              <PackageCheck :size="32" color="#d1d5db" /><p>暂无库存预警，一切正常</p>
            </div>
            <template v-else>
              <div class="alert-list" :class="{ 'alert-fade': alertTransitioning }">
                <div v-for="a in visibleAlerts" :key="a.id" class="alert-line">
                  <div class="alert-indicator" :class="a.reason === '已过期' ? 'indicator-danger' : 'indicator-warning'"></div>
                  <div class="alert-content">
                    <strong>{{ a.name }}</strong>
                    <p class="muted">{{ a.batch_no }} · {{ a.expires_on }}</p>
                  </div>
                  <el-tag :type="a.reason === '已过期' ? 'danger' : 'warning'" size="small" effect="dark" round>{{ a.reason }}</el-tag>
                </div>
              </div>
              <div class="alert-pagination" v-if="alertPages > 1">
                <button class="page-btn" :disabled="alertPage === 0" @click="changeAlertPage(-1)"><ChevronLeft :size="14" /></button>
                <span class="page-info">{{ alertPage + 1 }} / {{ alertPages }}</span>
                <button class="page-btn" :disabled="alertPage >= alertPages - 1" @click="changeAlertPage(1)"><ChevronRight :size="14" /></button>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 快捷入口 -->
      <section class="section section-animate" style="--delay: 0.65s">
        <h2 style="margin-bottom: 16px">快捷入口</h2>
        <div class="quick-grid">
          <router-link v-for="link in quickLinks.filter(l => auth.can(l.perm))" :key="link.path" :to="link.path" class="quick-card" :style="{ '--card-color': link.color }">
            <div class="quick-icon"><component :is="link.icon" :size="24" /></div>
            <span class="quick-label">{{ link.label }}</span>
            <ArrowUpRight :size="16" class="quick-arrow" />
          </router-link>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page-header-animate { animation: headerSlide 0.5s ease-out both; }
@keyframes headerSlide { from { opacity: 0; transform: translateY(-15px); } to { opacity: 1; transform: translateY(0); } }
.date-badge { display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px; background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 20px; font-size: 13px; }

/* 统计卡片 */
.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin: 20px 0 24px; }
.stat-card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; padding: 20px; display: flex; align-items: center; gap: 16px; position: relative; overflow: hidden; cursor: default; opacity: 0; transform: translateY(25px) scale(0.95); transition: all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1); transition-delay: var(--delay); }
.stat-card-visible { opacity: 1; transform: translateY(0) scale(1); }
.stat-card:hover { transform: translateY(-4px) scale(1.02); box-shadow: 0 12px 30px rgba(0,0,0,0.08); border-color: var(--accent); }
.stat-icon { width: 48px; height: 48px; background: var(--accent-bg); border-radius: 12px; display: grid; place-items: center; color: var(--accent); flex-shrink: 0; transition: transform 0.3s ease; }
.stat-card:hover .stat-icon { transform: scale(1.1) rotate(-5deg); }
.stat-info { display: flex; flex-direction: column; gap: 4px; }
.stat-label { font-size: 13px; color: var(--color-text-secondary); }
.stat-value { font-size: 28px; font-weight: 700; color: var(--color-text-primary); font-variant-numeric: tabular-nums; line-height: 1; }
.stat-glow { position: absolute; bottom: -20px; right: -20px; width: 80px; height: 80px; background: var(--accent); opacity: 0.06; border-radius: 50%; transition: all 0.4s ease; }
.stat-card:hover .stat-glow { width: 120px; height: 120px; opacity: 0.12; }

/* 图表网格 */
.charts-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 24px; }
.chart-card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; padding: 20px; transition: all 0.3s ease; position: relative; }
.chart-card:hover { box-shadow: 0 8px 25px rgba(0,0,0,0.06); transform: translateY(-2px); }
.chart-card h3 { font-size: 14px; font-weight: 600; color: var(--color-text-primary); margin: 0 0 12px; }
.chart-body { width: 100%; height: 220px; }
.chart-empty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: var(--color-text-placeholder); font-size: 14px; pointer-events: none; }

/* 下方并排面板（等高） */
.bottom-split { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px; }
.panel-card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; display: flex; flex-direction: column; overflow: hidden; }
.panel-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid var(--color-border-light); }
.panel-header h2 { font-size: 15px; font-weight: 600; margin: 0; display: flex; align-items: center; }
.panel-body { flex: 1; padding: 0; min-height: 280px; display: flex; flex-direction: column; }

/* 单据列表（替代表格，和预警等高） */
.doc-list { flex: 1; padding: 0 20px; transition: opacity 0.15s ease; }
.doc-line { display: flex; justify-content: space-between; align-items: center; padding: 14px 0; border-bottom: 1px solid var(--color-border-light); transition: background 0.2s; }
.doc-line:last-child { border-bottom: none; }
.doc-line:hover { background: var(--color-bg-hover); margin: 0 -12px; padding: 14px 12px; border-radius: 8px; }
.doc-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; flex: 1; }
.doc-number { font-size: 13px; font-weight: 500; color: var(--color-text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.doc-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--color-text-secondary); }
.doc-type { padding: 1px 6px; background: var(--color-bg-hover); border-radius: 4px; font-size: 11px; }
.doc-status { font-size: 12px; }
.doc-date { font-size: 12px; color: var(--color-text-placeholder); flex-shrink: 0; margin-left: 12px; }

.view-all { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; color: var(--color-primary); transition: gap 0.2s ease; }
.view-all:hover { gap: 8px; }

.status-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
.status-executed, .status-received { background: #059669; }
.status-submitted, .status-shipped { background: #d97706; }
.status-draft, .status-cancelled { background: #9ca3af; }
.status-approved { background: #2563eb; }

/* 预警 */
.alert-icon-pulse { animation: alertPulse 2s ease-in-out infinite; }
@keyframes alertPulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.2); } }
.alert-badge { display: inline-flex; align-items: center; justify-content: center; min-width: 20px; height: 20px; padding: 0 6px; background: #dc2626; color: white; border-radius: 10px; font-size: 11px; font-weight: 600; margin-left: 8px; }
.pulse-badge { animation: badgePulse 2s ease-in-out infinite; }
@keyframes badgePulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.4); } 50% { box-shadow: 0 0 0 8px rgba(220, 38, 38, 0); } }

.alert-list { flex: 1; padding: 0 20px; transition: opacity 0.15s ease; }
.alert-fade { opacity: 0; }
.alert-line { display: flex; align-items: center; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--color-border-light); transition: background 0.2s; }
.alert-line:last-child { border-bottom: none; }
.alert-line:hover { background: var(--color-bg-hover); margin: 0 -12px; padding: 14px 12px; border-radius: 8px; }
.alert-indicator { width: 3px; height: 28px; border-radius: 2px; flex-shrink: 0; }
.indicator-danger { background: #dc2626; }
.indicator-warning { background: #d97706; }
.alert-content { flex: 1; min-width: 0; }
.alert-content strong { font-size: 13px; display: block; }
.alert-content p { margin: 3px 0 0; font-size: 12px; }

/* 预警分页 */
.alert-pagination { display: flex; align-items: center; justify-content: center; gap: 12px; padding: 12px 20px; border-top: 1px solid var(--color-border-light); }
.page-btn { display: flex; align-items: center; justify-content: center; width: 28px; height: 28px; border: 1px solid var(--color-border); border-radius: 6px; background: var(--color-bg-card); cursor: pointer; color: var(--color-text-regular); transition: all 0.2s ease; }
.page-btn:hover:not(:disabled) { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-lighter); }
.page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.page-info { font-size: 12px; color: var(--color-text-secondary); min-width: 40px; text-align: center; }

.empty-state-mini { display: flex; flex-direction: column; align-items: center; justify-content: center; flex: 1; color: var(--color-text-placeholder); }
.empty-state-mini p { margin-top: 12px; font-size: 14px; }

/* 区块入场 */
.section-animate { opacity: 0; transform: translateY(20px); animation: sectionIn 0.6s ease-out forwards; animation-delay: var(--delay); }
@keyframes sectionIn { to { opacity: 1; transform: translateY(0); } }

/* 快捷入口 */
.quick-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; }
.quick-card { display: flex; align-items: center; gap: 14px; padding: 16px 20px; background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1); position: relative; overflow: hidden; }
.quick-card:hover { transform: translateY(-3px); box-shadow: 0 8px 25px rgba(0,0,0,0.08); border-color: var(--card-color); }
.quick-icon { width: 42px; height: 42px; background: color-mix(in srgb, var(--card-color) 12%, transparent); border-radius: 10px; display: grid; place-items: center; color: var(--card-color); transition: transform 0.3s ease; }
.quick-card:hover .quick-icon { transform: scale(1.1) rotate(-5deg); }
.quick-label { font-size: 14px; font-weight: 500; color: var(--color-text-primary); flex: 1; }
.quick-arrow { color: var(--color-text-placeholder); transition: all 0.3s ease; }
.quick-card:hover .quick-arrow { color: var(--card-color); transform: translate(3px, -3px); }

@media (max-width: 1200px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } .charts-grid { grid-template-columns: 1fr; } .bottom-split { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .stats-grid { grid-template-columns: 1fr; } }
</style>
