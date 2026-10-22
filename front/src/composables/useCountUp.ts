import { ref, onMounted, watch } from "vue";

/**
 * 数字从 0 滚动到目标值的动画 Hook
 */
export function useCountUp(target: number | (() => number), duration = 1500) {
  const current = ref(0);
  let rafId = 0;

  function animate(to: number) {
    const start = performance.now();
    const from = current.value;

    function tick(now: number) {
      const elapsed = now - start;
      const progress = Math.min(elapsed / duration, 1);
      // easeOutExpo 缓动
      const eased = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress);
      current.value = from + (to - from) * eased;

      if (progress < 1) {
        rafId = requestAnimationFrame(tick);
      } else {
        current.value = to;
      }
    }

    cancelAnimationFrame(rafId);
    rafId = requestAnimationFrame(tick);
  }

  const targetValue = typeof target === "function" ? target : () => target;

  onMounted(() => {
    animate(targetValue());
  });

  watch(
    typeof target === "function" ? target : () => target,
    (newVal) => {
      if (typeof newVal === "number") animate(newVal);
    }
  );

  return { current };
}

/**
 * 格式化数字显示
 */
export function formatNumber(value: number, decimals = 0): string {
  return value.toLocaleString("zh-CN", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}
