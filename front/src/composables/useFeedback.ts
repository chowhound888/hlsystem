import { ref, h } from "vue";
import { ElMessage, ElMessageBox, ElButton, ElProgress } from "element-plus";
import { LoaderCircle, Check, X } from "lucide-vue-next";

interface SuccessOptions {
  action?: {
    label: string;
    handler: () => void;
  };
  duration?: number;
}

interface ProgressHandle {
  setPercent: (percent: number) => void;
  setMessage: (message: string) => void;
  close: () => void;
  success: (message?: string) => void;
  error: (message?: string) => void;
}

interface UndoOptions {
  countdown?: number;
  onUndo: () => void;
  onConfirm?: () => void;
}

/**
 * 操作反馈增强 Hook
 */
export function useFeedback() {
  function success(message: string, options: SuccessOptions = {}): void {
    const { action, duration = 3000 } = options;

    if (action) {
      ElMessage({
        message: h("div", { style: "display: flex; align-items: center; gap: 12px;" }, [
          h(Check, { size: 16, color: "#67c23a" }),
          h("span", message),
          h(
            ElButton,
            {
              link: true,
              type: "primary",
              size: "small",
              onClick: () => {
                action.handler();
                document.querySelector(".el-message")?.remove();
              },
            },
            () => action.label
          ),
        ]),
        type: "success",
        duration: Math.max(duration, 5000),
        showClose: true,
      });
    } else {
      ElMessage({
        message: h("div", { style: "display: flex; align-items: center; gap: 8px;" }, [
          h(Check, { size: 16, color: "#67c23a" }),
          h("span", message),
        ]),
        type: "success",
        duration,
        showClose: true,
      });
    }
  }

  function error(message: string, duration = 5000): void {
    ElMessage({
      message: h("div", { style: "display: flex; align-items: center; gap: 8px;" }, [
        h(X, { size: 16, color: "#f56c6c" }),
        h("span", message),
      ]),
      type: "error",
      duration,
      showClose: true,
    });
  }

  function warning(message: string, duration = 5000): void {
    ElMessage.warning({ message, duration, showClose: true });
  }

  function info(message: string, duration = 4000): void {
    ElMessage.info({ message, duration, showClose: true });
  }

  function progress(initialMessage: string, initialPercent = 0): ProgressHandle {
    let currentPercent = initialPercent;
    let currentMessage = initialMessage;
    let status: "" | "success" | "exception" = "";

    const msgInstance = ElMessage({
      message: h("div", { style: "min-width: 200px;" }, [
        h("div", { style: "margin-bottom: 8px;" }, currentMessage),
        h(ElProgress, { percentage: currentPercent, strokeWidth: 6 }),
      ]),
      type: "info",
      duration: 0,
      showClose: false,
    });

    function update(): void {
      msgInstance.close();
      const newMsg = ElMessage({
        message: h("div", { style: "min-width: 200px;" }, [
          h("div", { style: "margin-bottom: 8px;" }, currentMessage),
          h(ElProgress, {
            percentage: currentPercent,
            status: status || undefined,
            strokeWidth: 6,
          }),
        ]),
        type: status === "exception" ? "error" : status === "success" ? "success" : "info",
        duration: status ? 3000 : 0,
        showClose: !!status,
      });
      Object.assign(msgInstance, newMsg);
    }

    return {
      setPercent(percent: number) {
        currentPercent = Math.min(100, Math.max(0, percent));
        update();
      },
      setMessage(message: string) {
        currentMessage = message;
        update();
      },
      close() {
        msgInstance.close();
      },
      success(message?: string) {
        currentPercent = 100;
        currentMessage = message || "操作完成";
        status = "success";
        update();
      },
      error(message?: string) {
        currentMessage = message || "操作失败";
        status = "exception";
        update();
      },
    };
  }

  function loading(message: string): () => void {
    const msgInstance = ElMessage({
      message: h("div", { style: "display: flex; align-items: center; gap: 8px;" }, [
        h(LoaderCircle, { size: 16, class: "animate-spin" }),
        h("span", message),
      ]),
      type: "info",
      duration: 0,
      showClose: false,
    });
    return () => msgInstance.close();
  }

  // === 撤销反馈 ===
  const undoState = ref<{
    message: string;
    countdown: number;
    timer: ReturnType<typeof setInterval> | null;
    onUndo: () => void;
    onConfirm?: () => void;
  } | null>(null);

  function showUndo(options: UndoOptions & { message: string }): void {
    const { message, countdown = 5, onUndo, onConfirm } = options;

    if (undoState.value?.timer) {
      clearInterval(undoState.value.timer);
    }

    let remaining = countdown;

    const timer = setInterval(() => {
      remaining--;
      if (undoState.value) {
        undoState.value.countdown = remaining;
      }
      if (remaining <= 0) {
        clearInterval(timer);
        if (onConfirm) onConfirm();
        undoState.value = null;
      }
    }, 1000);

    undoState.value = { message, countdown: remaining, timer, onUndo, onConfirm };

    ElMessage({
      message: h("div", { style: "display: flex; align-items: center; gap: 12px;" }, [
        h("span", message),
        h(
          ElButton,
          {
            link: true,
            type: "primary",
            size: "small",
            onClick: () => performUndo(),
          },
          () => "撤销"
        ),
        h("span", { style: "color: #909399; font-size: 12px;" }, `${remaining}s`),
      ]),
      type: "warning",
      duration: countdown * 1000,
      showClose: true,
      onClose: () => {
        if (undoState.value?.timer) {
          clearInterval(undoState.value.timer);
          if (onConfirm) onConfirm();
          undoState.value = null;
        }
      },
    });
  }

  function performUndo(): void {
    if (undoState.value) {
      if (undoState.value.timer) clearInterval(undoState.value.timer);
      undoState.value.onUndo();
      undoState.value = null;
      ElMessage.success("已撤销");
    }
  }

  function getUndoState() {
    return undoState;
  }

  async function confirmDanger(message: string, title = "确认操作"): Promise<boolean> {
    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText: "确认",
        cancelButtonText: "取消",
        type: "warning",
        confirmButtonClass: "el-button--danger",
      });
      return true;
    } catch {
      return false;
    }
  }

  async function confirmWithInput(
    message: string,
    expectedInput: string,
    title = "确认操作"
  ): Promise<boolean> {
    try {
      const { value } = await ElMessageBox.prompt(
        `${message}\n\n请输入 "${expectedInput}" 以确认：`,
        title,
        {
          confirmButtonText: "确认",
          cancelButtonText: "取消",
          inputPattern: new RegExp(`^${expectedInput}$`),
          inputErrorMessage: `输入内容必须为 "${expectedInput}"`,
          type: "warning",
        }
      );
      return value === expectedInput;
    } catch {
      return false;
    }
  }

  return {
    success,
    error,
    warning,
    info,
    progress,
    loading,
    showUndo,
    performUndo,
    getUndoState,
    confirmDanger,
    confirmWithInput,
  };
}
