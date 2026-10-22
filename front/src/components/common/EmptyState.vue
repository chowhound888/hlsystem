<script setup lang="ts">
import { PackageOpen } from "lucide-vue-next";

interface Props {
  message?: string;
  description?: string;
  icon?: any;
  actionText?: string;
}

withDefaults(defineProps<Props>(), {
  message: "暂无数据",
  description: "",
});

defineEmits<{
  action: [];
}>();
</script>

<template>
  <div class="empty-state">
    <div class="empty-icon">
      <component :is="icon || PackageOpen" :size="48" />
    </div>
    <p class="empty-message">{{ message }}</p>
    <p v-if="description" class="empty-description">{{ description }}</p>
    <el-button v-if="actionText" type="primary" @click="$emit('action')">
      {{ actionText }}
    </el-button>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-2xl) var(--space-md);
  text-align: center;
}

.empty-icon {
  color: var(--color-text-placeholder);
  margin-bottom: var(--space-md);
  opacity: 0.6;
}

.empty-message {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-xs);
}

.empty-description {
  font-size: var(--font-size-sm);
  color: var(--color-text-placeholder);
  margin: 0 0 var(--space-lg);
  max-width: 300px;
}
</style>
