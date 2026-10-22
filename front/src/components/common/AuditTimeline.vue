<script setup lang="ts">
import EmptyState from "./EmptyState.vue";

interface AuditAction {
  id: string | number;
  operator: string;
  action: string;
  occurred_at: string;
  reason?: string;
}

interface Props {
  actions: AuditAction[];
}

const props = defineProps<Props>();

const actionLabels: Record<string, string> = {
  CREATE: "创建",
  SUBMIT: "提交",
  APPROVE: "审核通过",
  REJECT: "驳回",
  CANCEL: "取消",
  EXECUTE: "执行",
  RECEIVE: "接收",
  VERIFY: "复核",
  DISPENSE: "发药",
  PAY: "缴费",
  PRICE: "划价",
  REFUND: "退款",
  REVERSE: "冲销",
};

function getActionLabel(action: string): string {
  return actionLabels[action] || action;
}
</script>

<template>
  <div class="audit-timeline">
    <el-timeline v-if="props.actions.length">
      <el-timeline-item
        v-for="action in props.actions"
        :key="action.id"
        :timestamp="action.occurred_at"
        placement="top"
      >
        <div class="timeline-content">
          <span class="timeline-operator">{{ action.operator }}</span>
          <span class="timeline-action">{{ getActionLabel(action.action) }}</span>
        </div>
        <p v-if="action.reason" class="timeline-reason">{{ action.reason }}</p>
      </el-timeline-item>
    </el-timeline>
    <EmptyState v-else message="暂无操作记录" />
  </div>
</template>

<style scoped>
.audit-timeline {
  padding: var(--space-md) 0;
}

.timeline-content {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}

.timeline-operator {
  font-weight: 500;
  color: var(--color-text-primary);
}

.timeline-action {
  color: var(--color-primary);
  font-size: var(--font-size-sm);
}

.timeline-reason {
  margin: var(--space-xs) 0 0;
  padding: var(--space-sm) var(--space-md);
  background: var(--color-bg-page);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

:deep(.el-timeline-item__timestamp) {
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
}
</style>
