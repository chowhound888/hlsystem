<script setup lang="ts">
import { ref, computed, watch, nextTick } from "vue";
import { useRouter, useRoute } from "vue-router";
import {
  LayoutDashboard,
  Pill,
  Warehouse,
  ClipboardList,
  Building2,
  Calculator,
  FlaskConical,
  ShieldCheck,
  Users,
  Settings,
  Menu,
  LogOut,
  ChevronDown,
  Activity,
} from "lucide-vue-next";
import { useAuth } from "../stores/auth";
import { useGlobalHotkeys } from "../composables/useHotkeys";
import Breadcrumb from "../components/common/Breadcrumb.vue";
import UndoBar from "../components/common/UndoBar.vue";
import HotkeyHelp from "../components/common/HotkeyHelp.vue";

const auth = useAuth();
const router = useRouter();
const route = useRoute();
const collapsed = ref(false);

// 初始化全局快捷键
useGlobalHotkeys();

const groups = [
  {
    name: "工作台",
    icon: LayoutDashboard,
    links: [["业务概览", "/workbench", ""]],
  },
  {
    name: "药品与资料",
    icon: Pill,
    links: [
      ["药品字典", "/master/drugs", "master:read"],
      ["物料字典", "/master/materials", "master:read"],
      ["供应商", "/master/suppliers", "master:read"],
      ["生产厂家", "/master/manufacturers", "master:read"],
      ["计量单位", "/master/units", "master:read"],
    ],
  },
  {
    name: "药品库房",
    icon: Warehouse,
    links: [
      ["库存查询", "/inventory/stocks", "inventory:read"],
      ["采购计划", "/warehouse/purchasing", "inventory:read"],
      ["采购入库", "/warehouse/receipts", "inventory:write"],
      ["调拨与出库", "/inventory/documents", "inventory:read"],
      ["领药申请", "/inventory/requisitions", "inventory:read"],
      ["在途异常", "/inventory/transit", "inventory:read"],
      ["库存流水", "/inventory/ledger", "inventory:read"],
      ["补货、盘点、结账", "/inventory/operations", "inventory:read"],
    ],
  },
  {
    name: "门诊药房",
    icon: ClipboardList,
    links: [["处方与发药", "/outpatient/prescriptions", "outpatient:read"]],
  },
  {
    name: "住院药房",
    icon: Building2,
    links: [
      ["医嘱与摆药", "/inpatient/orders", "inpatient:read"],
      ["病区基数药", "/inpatient/ward", "inpatient:read"],
    ],
  },
  {
    name: "会计与价格",
    icon: Calculator,
    links: [
      ["凭证与对账", "/accounting/vouchers", "accounting:read"],
      ["药品调价", "/pricing/adjustments", "pricing:write"],
    ],
  },
  {
    name: "制剂管理",
    icon: FlaskConical,
    links: [
      ["生产与质检", "/preparation/orders", "preparation:read"],
      ["原料质检与销售", "/preparation/quality-sales", "preparation:read"],
    ],
  },
  {
    name: "合理用药",
    icon: ShieldCheck,
    links: [["规则与知识", "/rational/rules", "rational:read"]],
  },
  {
    name: "统计报表",
    icon: Activity,
    links: [
      ["出入存与消耗", "/reports", "report:read"],
      ["全院核算分析", "/reports/analysis", "accounting:read"],
    ],
  },
  {
    name: "临床录入",
    icon: Users,
    links: [
      ["患者档案", "/clinical/patients", "clinical:read"],
      ["处方录入", "/clinical/prescriptions", "clinical:write"],
      ["医嘱录入", "/clinical/orders", "clinical:write"],
      ["住院登记", "/clinical/admissions", "clinical:write"],
    ],
  },
  {
    name: "系统管理",
    icon: Settings,
    links: [
      ["用户与角色", "/system/users", "system:read"],
      ["组织机构", "/system/locations", "system:read"],
      ["操作日志", "/system/audit-logs", "system:read"],
      ["打印模板", "/system/templates", "system:write"],
    ],
  },
];

const visible = computed(() =>
  groups
    .map((g) => ({
      ...g,
      links: g.links.filter((l) => !l[2] || auth.can(l[2])),
    }))
    .filter((g) => g.links.length),
);

async function logout() {
  await auth.logout();
  router.push("/login");
}

// 页面切换时滚动内容区到顶部，但不影响侧边栏
const mainContent = ref<HTMLElement>();
watch(() => route.path, () => {
  nextTick(() => {
    if (mainContent.value) {
      mainContent.value.scrollTop = 0;
    }
  });
});
</script>

<template>
  <div class="shell" :class="{ collapsed }">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <!-- 品牌区 -->
      <div class="brand">
        <div class="brand-mark">
          <Activity :size="22" />
        </div>
        <div class="brand-text">
          <span class="brand-name">药品管理分系统</span>
          <small>仁和教学医院</small>
        </div>
      </div>

      <!-- 导航菜单 -->
      <nav class="sidebar-nav">
        <div v-for="g in visible" :key="g.name" class="nav-group">
          <div class="nav-heading">
            <component :is="g.icon" :size="14" />
            <span>{{ g.name }}</span>
          </div>
          <router-link
            v-for="l in g.links"
            :key="l[1]"
            :to="l[1]"
            :title="l[0]"
            class="nav-item"
            active-class="active"
          >
            {{ l[0] }}
          </router-link>
        </div>
      </nav>

      <!-- 底部信息 -->
      <div class="sidebar-footer">
        <span>药事管理工作平台</span>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main">
      <!-- 顶栏 -->
      <header class="topbar">
        <button
          class="icon-button menu-toggle"
          title="展开或折叠菜单"
          @click="collapsed = !collapsed"
        >
          <Menu :size="18" />
        </button>
        
        <span class="breadcrumb">{{ route.meta.title }}</span>
        
        <div class="topbar-right">
          <el-select
            :model-value="auth.locationId"
            @update:model-value="auth.changeLocation"
            class="location-select"
            placeholder="选择机构"
          >
            <el-option
              v-for="l in auth.user?.locations"
              :key="l.id"
              :label="l.name"
              :value="l.id"
            />
          </el-select>
          
          <el-dropdown @command="logout" trigger="click">
            <span class="user-menu">
              <span class="avatar">{{ auth.user?.displayName.slice(0, 1) }}</span>
              <span class="user-name">{{ auth.user?.displayName }}</span>
              <ChevronDown :size="14" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <LogOut :size="14" style="margin-right: 8px" />
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="main-content" ref="mainContent">
        <Breadcrumb v-if="route.path !== '/workbench'" />
        <router-view v-slot="{ Component }">
          <transition name="page-slide" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>
    
    <!-- 全局撤销提示条 -->
    <UndoBar />
    
    <!-- 快捷键帮助面板 -->
    <HotkeyHelp />
  </div>
</template>

<style scoped>
/* === 整体布局 === */
.shell {
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  min-height: 100vh;
  transition: grid-template-columns 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.shell.collapsed {
  grid-template-columns: var(--sidebar-collapsed-width) minmax(0, 1fr);
}

/* === 侧边栏 === */
.sidebar {
  background: var(--sidebar-bg);
  border-right: 1px solid var(--sidebar-border);
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: var(--z-sticky);
}

/* 品牌区 */
.brand {
  height: var(--header-height);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-border-light);
  flex-shrink: 0;
  transition: padding 0.3s ease;
}

.brand-mark {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: white;
  border-radius: var(--radius-md);
  display: grid;
  place-items: center;
  flex-shrink: 0;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.3);
}
.brand:hover .brand-mark { transform: scale(1.1) rotate(-5deg); }

.brand-text {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: opacity 0.2s ease;
}

.brand-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.brand small {
  font-size: 11px;
  color: var(--color-text-secondary);
  margin-top: 2px;
}

/* 导航区域 */
.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px;
}

.sidebar-nav::-webkit-scrollbar { width: 4px; }
.sidebar-nav::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 2px;
}

.nav-group {
  margin-bottom: 4px;
  animation: navGroupIn 0.4s ease-out both;
}
.nav-group:nth-child(1) { animation-delay: 0.05s; }
.nav-group:nth-child(2) { animation-delay: 0.1s; }
.nav-group:nth-child(3) { animation-delay: 0.15s; }
.nav-group:nth-child(4) { animation-delay: 0.2s; }
.nav-group:nth-child(5) { animation-delay: 0.25s; }
.nav-group:nth-child(6) { animation-delay: 0.3s; }
.nav-group:nth-child(7) { animation-delay: 0.35s; }
.nav-group:nth-child(8) { animation-delay: 0.4s; }
.nav-group:nth-child(9) { animation-delay: 0.45s; }
.nav-group:nth-child(10) { animation-delay: 0.5s; }

@keyframes navGroupIn {
  from { opacity: 0; transform: translateX(-12px); }
  to { opacity: 1; transform: translateX(0); }
}

.nav-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  color: var(--color-text-secondary);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  transition: opacity 0.2s ease;
}

.nav-item {
  display: block;
  padding: 10px 12px 10px 34px;
  font-size: 13px;
  color: var(--color-text-regular);
  border-radius: var(--radius-md);
  margin: 2px 0;
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  position: relative;
  overflow: hidden;
}

.nav-item::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at var(--mx, 50%) var(--my, 50%), rgba(13, 148, 136, 0.08) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.3s;
}
.nav-item:hover::after { opacity: 1; }

.nav-item:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
  transform: translateX(4px);
}

.nav-item.active {
  background: var(--color-primary-lighter);
  color: var(--color-primary-dark);
  font-weight: 500;
  transform: translateX(4px);
}

/* 激活指示器 - 平滑滑动 */
.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  background: var(--color-primary);
  border-radius: 0 3px 3px 0;
  animation: indicatorIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
@keyframes indicatorIn {
  from { transform: scaleY(0); }
  to { transform: scaleY(1); }
}

/* 侧边栏底部 */
.sidebar-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--color-border-light);
  flex-shrink: 0;
  transition: opacity 0.2s ease;
}

.sidebar-footer span {
  font-size: 11px;
  color: var(--color-text-secondary);
}

/* === 折叠状态 - 弹性过渡 === */
.collapsed .brand-text,
.collapsed .nav-heading span,
.collapsed .nav-item,
.collapsed .sidebar-footer {
  opacity: 0;
  pointer-events: none;
  position: absolute;
  visibility: hidden;
}

.collapsed .brand {
  justify-content: center;
  padding: 0;
}

.collapsed .sidebar-nav {
  padding: 8px;
}

.collapsed .nav-heading {
  justify-content: center;
  padding: 12px 0;
}

.collapsed .brand-mark {
  transform: scale(1.15);
}

/* === 主内容区 === */
.main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

/* 顶栏 */
.topbar {
  height: var(--header-height);
  background: var(--header-bg);
  border-bottom: 1px solid var(--header-border);
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  backdrop-filter: blur(12px);
  background: rgba(255, 255, 255, 0.85);
}

.menu-toggle {
  flex-shrink: 0;
  transition: all 0.2s ease;
}
.menu-toggle:hover {
  background: var(--color-primary-lighter);
  color: var(--color-primary);
  border-color: var(--color-primary-light);
}
.menu-toggle:active { transform: scale(0.9); }

.breadcrumb {
  font-size: 14px;
  color: var(--color-text-secondary);
  font-weight: 500;
  animation: breadcrumbIn 0.3s ease-out;
}
@keyframes breadcrumbIn {
  from { opacity: 0; transform: translateX(-10px); }
  to { opacity: 1; transform: translateX(0); }
}

.topbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 16px;
}

.location-select { width: 180px; }
:deep(.location-select .el-select__wrapper) { border-radius: var(--radius-sm); }

.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: var(--radius-md);
  transition: all 0.2s ease;
}
.user-menu:hover {
  background: var(--color-bg-hover);
  transform: translateY(-1px);
}

.avatar {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, var(--color-primary-light), var(--color-primary));
  color: white;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.user-menu:hover .avatar { transform: scale(1.1); }

.user-name {
  font-size: 14px;
  color: var(--color-text-regular);
}

/* 主内容 */
.main-content {
  flex: 1;
  padding: 24px;
  max-width: 1600px;
  width: 100%;
  margin: 0 auto;
  overflow-y: auto;
  scroll-behavior: smooth;
}

/* === 响应式 === */
@media (max-width: 768px) {
  .shell { grid-template-columns: minmax(0, 1fr); }
  .sidebar {
    position: fixed; left: 0; top: 0;
    width: var(--sidebar-width);
    transform: translateX(-100%);
    transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
    box-shadow: var(--shadow-xl);
  }
  .shell:not(.collapsed) .sidebar { transform: translateX(0); }
  .collapsed .sidebar { transform: translateX(-100%); }
  .topbar { padding: 0 16px; }
  .breadcrumb { display: none; }
  .location-select { width: 140px; }
  .user-name { display: none; }
  .main-content { padding: 16px; }
}
</style>
