import { ref, onMounted, onBeforeUnmount, watch, type Ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";

interface DraftOptions {
  /** 自动保存间隔（毫秒），默认 30 秒 */
  autoSaveInterval?: number;
  /** 是否在挂载时自动恢复草稿，默认 true */
  autoRestore?: boolean;
  /** 保存成功时的提示持续时间（毫秒），0 表示不提示 */
  saveToastDuration?: number;
}

interface DraftState<T> {
  /** 是否有未保存的草稿 */
  hasDraft: Ref<boolean>;
  /** 最后保存时间 */
  lastSavedAt: Ref<Date | null>;
  /** 恢复草稿 */
  restore: () => boolean;
  /** 手动保存草稿 */
  save: () => void;
  /** 清除草稿 */
  clear: () => void;
  /** 检查并提示恢复草稿 */
  checkAndPromptRestore: () => Promise<boolean>;
}

/**
 * 表单草稿自动保存 Hook
 * 
 * @example
 * ```ts
 * const form = reactive({ name: '', amount: 0 });
 * const { hasDraft, restore, clear } = useFormDraft('drug-edit', form);
 * 
 * // 组件挂载时自动恢复
 * onMounted(() => {
 *   if (hasDraft.value) {
 *     restore();
 *   }
 * });
 * 
 * // 提交成功后清除草稿
 * async function submit() {
 *   await saveData(form);
 *   clear();
 * }
 * ```
 */
export function useFormDraft<T extends Record<string, any>>(
  key: string,
  form: T,
  options: DraftOptions = {}
): DraftState<T> {
  const {
    autoSaveInterval = 30000,
    autoRestore = true,
    saveToastDuration = 2000,
  } = options;

  const storageKey = `hlsystem_draft_${key}`;
  const hasDraft = ref(false);
  const lastSavedAt = ref<Date | null>(null);
  let saveTimer: ReturnType<typeof setInterval> | null = null;
  let isRestoring = false;

  // 检查是否存在草稿
  function checkDraft(): boolean {
    const saved = localStorage.getItem(storageKey);
    hasDraft.value = !!saved;
    return hasDraft.value;
  }

  // 保存草稿到 localStorage
  function save(): void {
    try {
      const data = {
        formData: JSON.parse(JSON.stringify(form)),
        savedAt: new Date().toISOString(),
        version: 1,
      };
      localStorage.setItem(storageKey, JSON.stringify(data));
      lastSavedAt.value = new Date();
      hasDraft.value = true;

      if (saveToastDuration > 0) {
        ElMessage({
          message: "草稿已自动保存",
          type: "success",
          duration: saveToastDuration,
          grouping: true,
        });
      }
    } catch (e) {
      console.warn("Failed to save draft:", e);
    }
  }

  // 从 localStorage 恢复草稿
  function restore(): boolean {
    try {
      const saved = localStorage.getItem(storageKey);
      if (!saved) return false;

      const data = JSON.parse(saved);
      if (data.formData) {
        isRestoring = true;
        Object.assign(form, data.formData);
        lastSavedAt.value = new Date(data.savedAt);
        
        // 延迟重置标志，避免触发自动保存
        setTimeout(() => {
          isRestoring = false;
        }, 100);

        return true;
      }
    } catch (e) {
      console.warn("Failed to restore draft:", e);
    }
    return false;
  }

  // 清除草稿
  function clear(): void {
    localStorage.removeItem(storageKey);
    hasDraft.value = false;
    lastSavedAt.value = null;
  }

  // 检查并提示用户是否恢复草稿
  async function checkAndPromptRestore(): Promise<boolean> {
    if (!checkDraft()) return false;

    try {
      const saved = localStorage.getItem(storageKey);
      if (!saved) return false;

      const data = JSON.parse(saved);
      const savedTime = new Date(data.savedAt).toLocaleString("zh-CN");

      await ElMessageBox.confirm(
        `检测到上次未完成的草稿（保存于 ${savedTime}），是否恢复？`,
        "恢复草稿",
        {
          confirmButtonText: "恢复草稿",
          cancelButtonText: "丢弃草稿",
          distinguishCancelAndClose: true,
          type: "info",
        }
      );

      // 用户选择恢复
      restore();
      ElMessage.success("草稿已恢复");
      return true;
    } catch (action) {
      if (action === "cancel") {
        // 用户选择丢弃
        clear();
        ElMessage.info("草稿已丢弃");
      }
      // action === 'close' 时不做任何操作
      return false;
    }
  }

  // 启动自动保存定时器
  function startAutoSave(): void {
    if (saveTimer) return;

    saveTimer = setInterval(() => {
      // 只在表单有内容且不在恢复过程中时保存
      if (!isRestoring && hasContent()) {
        save();
      }
    }, autoSaveInterval);
  }

  // 停止自动保存
  function stopAutoSave(): void {
    if (saveTimer) {
      clearInterval(saveTimer);
      saveTimer = null;
    }
  }

  // 检查表单是否有内容
  function hasContent(): boolean {
    return Object.values(form).some((value) => {
      if (value === null || value === undefined || value === "") return false;
      if (Array.isArray(value)) return value.length > 0;
      if (typeof value === "object") return Object.keys(value).length > 0;
      return true;
    });
  }

  // 页面卸载前保存
  function handleBeforeUnload(e: BeforeUnloadEvent): void {
    if (hasContent()) {
      save();
      // 现代浏览器会显示默认提示
      e.preventDefault();
      e.returnValue = "";
    }
  }

  // 生命周期钩子
  onMounted(() => {
    checkDraft();
    startAutoSave();
    window.addEventListener("beforeunload", handleBeforeUnload);

    if (autoRestore && hasDraft.value) {
      // 延迟执行，让组件先完成初始化
      setTimeout(() => {
        checkAndPromptRestore();
      }, 500);
    }
  });

  onBeforeUnmount(() => {
    stopAutoSave();
    window.removeEventListener("beforeunload", handleBeforeUnload);
    
    // 组件卸载时保存一次
    if (hasContent()) {
      save();
    }
  });

  // 监听表单变化，标记为有草稿
  watch(
    () => form,
    () => {
      if (!isRestoring && hasContent()) {
        hasDraft.value = true;
      }
    },
    { deep: true }
  );

  return {
    hasDraft,
    lastSavedAt,
    restore,
    save,
    clear,
    checkAndPromptRestore,
  };
}
