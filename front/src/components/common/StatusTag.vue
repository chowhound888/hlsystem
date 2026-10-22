<script setup lang="ts">
import { computed } from "vue";

interface Props {
  status: string;
  type?: "success" | "warning" | "danger" | "info" | "primary";
  size?: "small" | "default" | "large";
}

const props = withDefaults(defineProps<Props>(), {
  size: "small",
});

// 状态配置映射
const statusConfig: Record<string, { label: string; type: string }> = {
  // 单据状态
  DRAFT: { label: "草稿", type: "info" },
  SUBMITTED: { label: "待审核", type: "warning" },
  APPROVED: { label: "已审核", type: "primary" },
  EXECUTED: { label: "已执行", type: "success" },
  CANCELLED: { label: "已取消", type: "info" },
  REJECTED: { label: "已驳回", type: "danger" },
  SHIPPED: { label: "待接收", type: "warning" },
  RECEIVED: { label: "已接收", type: "success" },
  PARTIALLY_RECEIVED: { label: "部分接收", type: "warning" },
  REVERSED: { label: "已冲销", type: "info" },

  // 处方状态
  PRICED: { label: "待缴费", type: "warning" },
  PAID: { label: "待复核", type: "primary" },
  VERIFIED: { label: "待发药", type: "warning" },
  DISPENSED: { label: "已发药", type: "success" },

  // 库存状态
  AVAILABLE: { label: "可用", type: "success" },
  PENDING_QC: { label: "待检", type: "warning" },
  QUARANTINE: { label: "隔离", type: "danger" },

  // 质量状态
  PASSED: { label: "合格", type: "success" },
  FAILED: { label: "不合格", type: "danger" },
  PENDING: { label: "待检", type: "warning" },

  // 启用状态
  ENABLED: { label: "启用", type: "success" },
  DISABLED: { label: "停用", type: "info" },

  // 审查结果
  CLEAR: { label: "通过", type: "success" },
  WARNING: { label: "警告", type: "warning" },
  BLOCKED: { label: "阻断", type: "danger" },
  INCOMPLETE: { label: "资料不足", type: "info" },

  // 退款状态
  REFUNDED: { label: "已退款", type: "success" },
  PENDING_REFUND: { label: "待退款", type: "warning" },

  // 凭证状态
  POSTED: { label: "已过账", type: "success" },
  UNPOSTED: { label: "未过账", type: "warning" },

  // 结账状态
  OPEN: { label: "开放", type: "success" },
  CLOSED: { label: "已结账", type: "info" },
};

const config = computed(() => {
  const defaultConfig = { label: props.status, type: props.type || "info" };
  return statusConfig[props.status] || defaultConfig;
});

const tagType = computed(() => props.type || config.value.type);
const tagLabel = computed(() => config.value.label);
</script>

<template>
  <el-tag :type="tagType as any" :size="size" effect="light" round>
    {{ tagLabel }}
  </el-tag>
</template>

<style scoped>
:deep(.el-tag) {
  font-weight: 500;
}
</style>
