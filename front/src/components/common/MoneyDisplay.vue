<script setup lang="ts">
import { computed } from "vue";

interface Props {
  value: number | string | null | undefined;
  prefix?: string;
  precision?: number;
  color?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  prefix: "¥",
  precision: 2,
  color: false,
});

const formattedValue = computed(() => {
  const num = Number(props.value) || 0;
  return num.toFixed(props.precision);
});

const isNegative = computed(() => Number(props.value) < 0);
const isPositive = computed(() => Number(props.value) > 0);
</script>

<template>
  <span 
    class="money-display" 
    :class="{ 
      'money-negative': color && isNegative, 
      'money-positive': color && isPositive 
    }"
  >
    {{ prefix }}{{ formattedValue }}
  </span>
</template>

<style scoped>
.money-display {
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum";
}

.money-negative {
  color: var(--color-danger);
}

.money-positive {
  color: var(--color-success);
}
</style>
