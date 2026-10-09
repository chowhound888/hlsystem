import { createRouter, createWebHistory } from "vue-router";
import { useAuth } from "../stores/auth";
import { refreshCsrf } from "../api/client";
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      component: () => import("../modules/system/LoginView.vue"),
    },
    {
      path: "/print/:kind/:id",
      component: () => import("../modules/reporting/PrintView.vue"),
    },
    {
      path: "/",
      component: () => import("../layouts/MainLayout.vue"),
      children: [
        { path: "", redirect: "/workbench" },
        {
          path: "workbench",
          component: () => import("../modules/workbench/WorkbenchView.vue"),
          meta: { title: "工作台" },
        },
        {
          path: "master/drugs",
          component: () => import("../modules/master/DrugsView.vue"),
          meta: { title: "药品字典", permission: "master:read" },
        },
        {
          path: "master/materials",
          component: () => import("../modules/master/MaterialsView.vue"),
          meta: { title: "物料字典", permission: "master:read" },
        },
        {
          path: "master/:type",
          component: () => import("../modules/master/CatalogView.vue"),
          meta: { title: "基础资料", permission: "master:read" },
        },
        {
          path: "inventory/operations",
          component: () => import("../modules/inventory/OperationsView.vue"),
          meta: { title: "盘点与结账", permission: "inventory:read" },
        },
        {
          path: "inventory/requisitions",
          component: () => import("../modules/inventory/RequisitionsView.vue"),
          meta: { title: "领药申请", permission: "inventory:read" },
        },
        {
          path: "inventory/transit",
          component: () => import("../modules/inventory/TransitView.vue"),
          meta: { title: "在途异常", permission: "inventory:read" },
        },
        {
          path: "inventory/:type",
          component: () => import("../modules/inventory/InventoryView.vue"),
          meta: { title: "库存管理", permission: "inventory:read" },
        },
        {
          path: "warehouse/receipts",
          component: () => import("../modules/inventory/InventoryView.vue"),
          meta: { title: "采购与入库", permission: "inventory:write" },
        },
        {
          path: "warehouse/purchasing",
          component: () => import("../modules/warehouse/PurchasingView.vue"),
          meta: { title: "采购计划", permission: "inventory:read" },
        },
        {
          path: "clinical/prescriptions",
          component: () =>
            import("../modules/outpatient/PrescriptionsView.vue"),
          meta: { title: "处方录入", permission: "clinical:write" },
        },
        {
          path: "clinical/:type",
          component: () => import("../modules/clinical/ClinicalView.vue"),
          meta: { title: "临床登记", permission: "clinical:read" },
        },
        {
          path: "outpatient/prescriptions",
          component: () =>
            import("../modules/outpatient/PrescriptionsView.vue"),
          meta: { title: "门诊处方", permission: "outpatient:read" },
        },
        {
          path: "inpatient/orders",
          component: () => import("../modules/inpatient/OrdersView.vue"),
          meta: { title: "住院医嘱", permission: "inpatient:read" },
        },
        {
          path: "inpatient/ward",
          component: () => import("../modules/inpatient/WardView.vue"),
          meta: { title: "病区基数药", permission: "inpatient:read" },
        },
        {
          path: "accounting/vouchers",
          component: () => import("../modules/accounting/AccountingView.vue"),
          meta: { title: "药品会计", permission: "accounting:read" },
        },
        {
          path: "pricing/adjustments",
          component: () => import("../modules/accounting/AccountingView.vue"),
          meta: { title: "药品调价", permission: "pricing:write" },
        },
        {
          path: "preparation/orders",
          component: () => import("../modules/preparation/PreparationView.vue"),
          meta: { title: "制剂生产", permission: "preparation:read" },
        },
        {
          path: "preparation/quality-sales",
          component: () => import("../modules/preparation/QualitySales.vue"),
          meta: { title: "原料质检与销售", permission: "preparation:read" },
        },
        {
          path: "rational/rules",
          component: () => import("../modules/rational/RationalView.vue"),
          meta: { title: "合理用药", permission: "rational:read" },
        },
        {
          path: "reports",
          component: () => import("../modules/reporting/ReportsView.vue"),
          meta: { title: "统计报表", permission: "report:read" },
        },
        {
          path: "reports/analysis",
          component: () => import("../modules/reporting/AnalysisView.vue"),
          meta: { title: "全院核算分析", permission: "accounting:read" },
        },
        {
          path: "clinical/orders",
          component: () => import("../modules/inpatient/OrdersView.vue"),
          meta: { title: "医嘱录入", permission: "clinical:write" },
        },
        {
          path: "system/templates",
          component: () => import("../modules/system/TemplatesView.vue"),
          meta: { title: "打印模板", permission: "system:write" },
        },
        {
          path: "system/:type",
          component: () => import("../modules/system/SystemView.vue"),
          meta: { title: "系统管理", permission: "system:read" },
        },
      ],
    },
  ],
});
router.beforeEach(async (to) => {
  const auth = useAuth();
  if (to.path === "/login") return true;
  if (!auth.user) {
    try {
      await refreshCsrf();
      await auth.load();
    } catch {
      return "/login";
    }
  }
  if (to.meta.permission && !auth.can(String(to.meta.permission)))
    return "/workbench";
  return true;
});
