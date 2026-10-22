import { ref, onMounted, onUnmounted } from "vue";

interface AutoRefreshOptions {
  /** 刷新间隔（毫秒），默认 60 秒 */
  interval?: number;
  /** 是否在页面重新可见时立即刷新，默认 true */
  refreshOnVisible?: boolean;
  /** 是否启用自动刷新，默认 true */
  enabled?: boolean;
}

/**
 * 自动刷新 Hook
 * 
 * - 定时刷新数据
 * - 页面重新获得焦点时刷新
 * - 支持暂停/恢复
 * 
 * @example
 * ```ts
 * const { isPaused, pause, resume } = useAutoRefresh(loadData, {
 *   interval: 30000, // 30秒刷新一次
 * });
 * 
 * // 用户操作时暂停自动刷新
 * function startEditing() {
 *   pause();
 * }
 * 
 * // 编辑完成后恢复
 * function finishEditing() {
 *   resume();
 * }
 * ```
 */
export function useAutoRefresh(
  loadFn: () => Promise<void> | void,
  options: AutoRefreshOptions = {}
) {
  const {
    interval = 60000,
    refreshOnVisible = true,
    enabled = true,
  } = options;

  const isPaused = ref(false);
  const lastRefreshAt = ref<Date | null>(null);
  let timer: ReturnType<typeof setInterval> | null = null;
  let isEnabled = enabled;

  // 执行刷新
  async function refresh(): Promise<void> {
    if (isPaused.value || !isEnabled) return;
    
    try {
      await loadFn();
      lastRefreshAt.value = new Date();
    } catch (e) {
      console.warn("Auto refresh failed:", e);
    }
  }

  // 启动定时器
  function startTimer(): void {
    if (timer || !isEnabled) return;
    
    timer = setInterval(() => {
      // 只在页面可见时刷新
      if (!document.hidden) {
        refresh();
      }
    }, interval);
  }

  // 停止定时器
  function stopTimer(): void {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  // 暂停自动刷新
  function pause(): void {
    isPaused.value = true;
  }

  // 恢复自动刷新
  function resume(): void {
    isPaused.value = false;
    // 恢复后立即刷新一次
    refresh();
  }

  // 手动触发刷新
  function manualRefresh(): void {
    refresh();
  }

  // 页面可见性变化处理
  function handleVisibilityChange(): void {
    if (refreshOnVisible && !document.hidden && !isPaused.value) {
      refresh();
    }
  }

  // 窗口获得焦点处理
  function handleFocus(): void {
    if (refreshOnVisible && !isPaused.value) {
      refresh();
    }
  }

  // 生命周期
  onMounted(() => {
    if (isEnabled) {
      startTimer();
      document.addEventListener("visibilitychange", handleVisibilityChange);
      window.addEventListener("focus", handleFocus);
    }
  });

  onUnmounted(() => {
    stopTimer();
    document.removeEventListener("visibilitychange", handleVisibilityChange);
    window.removeEventListener("focus", handleFocus);
  });

  return {
    isPaused,
    lastRefreshAt,
    pause,
    resume,
    manualRefresh,
  };
}
