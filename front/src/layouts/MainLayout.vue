<script setup lang="ts">
import { ref, computed } from "vue";
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
const auth = useAuth(),
  router = useRouter(),
  route = useRoute(),
  collapsed = ref(false);
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
</script>
<template>
  <div class="shell" :class="{ collapsed }">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark"><Activity :size="23" /></div>
        <div class="brand-text">药品管理分系统<small>仁和教学医院</small></div>
      </div>
      <nav>
        <div v-for="g in visible" :key="g.name" class="nav-group">
          <div class="nav-heading">
            <component :is="g.icon" :size="16" /><span>{{ g.name }}</span>
          </div>
          <router-link
            v-for="l in g.links"
            :key="l[1]"
            :to="l[1]"
            :title="l[0]"
            >{{ l[0] }}</router-link
          >
        </div>
      </nav>
      <div class="sidebar-foot">药事管理工作平台</div>
    </aside>
    <div class="main">
      <header class="topbar">
        <button
          class="icon-button"
          title="展开或折叠菜单"
          @click="collapsed = !collapsed"
        >
          <Menu :size="18" /></button
        ><span class="breadcrumb">{{ route.meta.title }}</span>
        <div class="topbar-right">
          <el-select
            :model-value="auth.locationId"
            @update:model-value="auth.changeLocation"
            style="width: 180px"
            ><el-option
              v-for="l in auth.user?.locations"
              :key="l.id"
              :label="l.name"
              :value="l.id" /></el-select
          ><el-dropdown @command="logout"
            ><span class="user-menu"
              ><span class="avatar">{{
                auth.user?.displayName.slice(0, 1)
              }}</span
              >{{ auth.user?.displayName }}<ChevronDown :size="14" /></span
            ><template #dropdown
              ><el-dropdown-menu
                ><el-dropdown-item command="logout"
                  ><LogOut :size="14" /> 退出登录</el-dropdown-item
                ></el-dropdown-menu
              ></template
            ></el-dropdown
          >
        </div>
      </header>
      <main class="main-content">
        <router-view :key="route.path + auth.locationId" />
      </main>
    </div>
  </div>
</template>
<style scoped>
.shell {
  display: grid;
  grid-template-columns: 216px minmax(0, 1fr);
  min-height: 100vh;
}
.sidebar {
  background: #fff;
  border-right: 1px solid #dde1e6;
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
}
.brand {
  height: 76px;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e8ebef;
  flex-shrink: 0;
}
.brand-mark {
  background: #15803d;
  color: white;
  width: 36px;
  height: 36px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.brand-text {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}
.brand small {
  display: block;
  font-size: 11px;
  font-weight: 400;
  color: #59616d;
  margin-top: 5px;
}
nav {
  overflow: auto;
  padding: 8px 12px;
  flex: 1;
}
.nav-group {
  padding: 6px 0;
}
.nav-heading {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px;
  color: #69717d;
  font-size: 12px;
}
.nav-group a {
  display: block;
  padding: 9px 12px 9px 33px;
  font-size: 13px;
  border-radius: 4px;
  margin: 2px 0;
}
.nav-group a:hover {
  background: #f5f6f8;
}
.nav-group a.router-link-active {
  background: #edf3ff;
  color: #2563eb;
  font-weight: 600;
}
.sidebar-foot {
  padding: 12px 20px;
  font-size: 11px;
  color: #777;
  border-top: 1px solid #dde1e6;
}
.topbar {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #dde1e6;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px;
}
.breadcrumb {
  font-size: 13px;
  color: #59616d;
}
.topbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 24px;
}
.user-menu {
  display: flex;
  gap: 8px;
  align-items: center;
  cursor: pointer;
  font-size: 13px;
}
.avatar {
  width: 28px;
  height: 28px;
  background: #e8edf4;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 12px;
  color: #445062;
}
.main {
  min-width: 0;
}
.main-content {
  padding: 24px;
  max-width: 1800px;
  margin: auto;
}
.collapsed {
  grid-template-columns: 64px minmax(0, 1fr);
}
.collapsed .brand-text,
.collapsed .nav-heading span,
.collapsed .nav-group a,
.collapsed .sidebar-foot {
  display: none;
}
.collapsed .brand {
  padding: 14px;
}
.collapsed nav {
  padding: 8px;
}
.collapsed .nav-heading {
  justify-content: center;
  padding: 10px 0;
}
@media (max-width: 768px) {
  .shell {
    grid-template-columns: minmax(0, 1fr);
  }
  .sidebar {
    position: fixed;
    z-index: 100;
    left: 0;
    width: 216px;
  }
  .collapsed .sidebar {
    display: none;
  }
  .topbar {
    padding: 0 12px;
  }
  .topbar-right {
    gap: 12px;
  }
  .breadcrumb {
    display: none;
  }
  .main-content {
    padding: 16px;
  }
  .user-menu {
    font-size: 0;
  }
}
</style>
<style scoped>
@media (max-width: 768px) {
  .sidebar {
    top: 60px;
    height: calc(100vh - 60px);
  }
  .topbar {
    position: relative;
    z-index: 101;
  }
  .user-menu .avatar {
    font-size: 12px;
  }
}
</style>
