import { onMounted, onUnmounted, ref } from "vue";

interface HotkeyConfig {
  key: string;
  handler: (e: KeyboardEvent) => void;
  allowInInput?: boolean;
  description?: string;
}

// 全局快捷键注册表
const globalHotkeys: HotkeyConfig[] = [];
const showHelpPanel = ref(false);

function parseHotkey(key: string): {
  ctrl: boolean;
  shift: boolean;
  alt: boolean;
  meta: boolean;
  key: string;
} {
  const parts = key.toLowerCase().split("+");
  return {
    ctrl: parts.includes("ctrl") || parts.includes("control"),
    shift: parts.includes("shift"),
    alt: parts.includes("alt"),
    meta: parts.includes("meta") || parts.includes("cmd"),
    key: parts[parts.length - 1],
  };
}

function matchesHotkey(e: KeyboardEvent, hotkey: string): boolean {
  const parsed = parseHotkey(hotkey);
  return (
    e.ctrlKey === parsed.ctrl &&
    e.shiftKey === parsed.shift &&
    e.altKey === parsed.alt &&
    e.metaKey === parsed.meta &&
    e.key.toLowerCase() === parsed.key
  );
}

function isInputElement(el: EventTarget | null): boolean {
  if (!el || !(el instanceof HTMLElement)) return false;
  const tagName = el.tagName.toLowerCase();
  return (
    tagName === "input" ||
    tagName === "textarea" ||
    tagName === "select" ||
    el.isContentEditable
  );
}

/**
 * 组件级快捷键 Hook
 */
export function useHotkeys(hotkeys: HotkeyConfig[]): void {
  function handleKeydown(e: KeyboardEvent): void {
    for (const hotkey of hotkeys) {
      if (matchesHotkey(e, hotkey.key)) {
        if (!hotkey.allowInInput && isInputElement(e.target)) continue;
        e.preventDefault();
        e.stopPropagation();
        hotkey.handler(e);
        return;
      }
    }
  }

  onMounted(() => {
    document.addEventListener("keydown", handleKeydown);
  });

  onUnmounted(() => {
    document.removeEventListener("keydown", handleKeydown);
  });
}

/**
 * 全局快捷键（在 MainLayout 中调用一次）
 */
export function useGlobalHotkeys(): void {
  const globalHandlers: HotkeyConfig[] = [
    {
      key: "ctrl+k",
      handler: () => {
        window.dispatchEvent(new CustomEvent("open-command-palette"));
      },
      description: "打开命令面板",
    },
    {
      key: "ctrl+/",
      handler: () => {
        showHelpPanel.value = !showHelpPanel.value;
      },
      description: "显示快捷键帮助",
    },
    {
      key: "escape",
      handler: () => {
        if (showHelpPanel.value) {
          showHelpPanel.value = false;
        }
      },
      allowInInput: true,
      description: "关闭弹窗",
    },
    ...Array.from({ length: 9 }, (_, i) => ({
      key: `alt+${i + 1}`,
      handler: () => {
        window.dispatchEvent(
          new CustomEvent("navigate-to-menu", { detail: { index: i } })
        );
      },
      description: `导航到第 ${i + 1} 个菜单组`,
    })),
  ];

  // 避免重复注册
  if (globalHotkeys.length === 0) {
    globalHotkeys.push(...globalHandlers);
  }

  function handleKeydown(e: KeyboardEvent): void {
    for (const hotkey of globalHotkeys) {
      if (matchesHotkey(e, hotkey.key)) {
        if (!hotkey.allowInInput && isInputElement(e.target)) continue;
        e.preventDefault();
        hotkey.handler(e);
        return;
      }
    }
  }

  onMounted(() => {
    document.addEventListener("keydown", handleKeydown);
  });

  onUnmounted(() => {
    document.removeEventListener("keydown", handleKeydown);
  });
}

export function getRegisteredHotkeys(): HotkeyConfig[] {
  return globalHotkeys.filter((h) => h.description);
}

export function formatHotkey(key: string): string {
  return key
    .toLowerCase()
    .replace("ctrl", "Ctrl")
    .replace("shift", "Shift")
    .replace("alt", "Alt")
    .replace("meta", "⌘")
    .split("+")
    .map((s) => s.trim())
    .join(" + ");
}
