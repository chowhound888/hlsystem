<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { Keyboard } from "lucide-vue-next";
import { getRegisteredHotkeys, formatHotkey } from "../../composables/useHotkeys";

const visible = ref(false);
const hotkeys = ref(getRegisteredHotkeys());

function toggle() {
  visible.value = !visible.value;
}

function handleKeydown(e: KeyboardEvent) {
  if (e.ctrlKey && e.key === "/") {
    e.preventDefault();
    toggle();
  }
  if (e.key === "Escape" && visible.value) {
    visible.value = false;
  }
}

onMounted(() => {
  document.addEventListener("keydown", handleKeydown);
});

onUnmounted(() => {
  document.removeEventListener("keydown", handleKeydown);
});
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="hotkey-overlay" @click.self="visible = false">
        <div class="hotkey-panel">
          <div class="hotkey-header">
            <Keyboard :size="20" />
            <h3>键盘快捷键</h3>
            <button class="close-btn" @click="visible = false">&times;</button>
          </div>

          <div class="hotkey-list">
            <div
              v-for="(hotkey, index) in hotkeys"
              :key="index"
              class="hotkey-item"
            >
              <span class="hotkey-desc">{{ hotkey.description }}</span>
              <kbd class="hotkey-key">{{ formatHotkey(hotkey.key) }}</kbd>
            </div>
            <div v-if="!hotkeys.length" class="hotkey-empty">
              暂无已注册的快捷键
            </div>
          </div>

          <div class="hotkey-footer">
            按 <kbd>Ctrl</kbd> + <kbd>/</kbd> 随时查看此帮助
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.hotkey-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
  backdrop-filter: blur(2px);
}

.hotkey-panel {
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.hotkey-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}

.hotkey-header h3 {
  margin: 0;
  font-size: 18px;
  flex: 1;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: #6b7280;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  line-height: 1;
}

.close-btn:hover {
  background: #f3f4f6;
}

.hotkey-list {
  padding: 16px 24px;
  overflow-y: auto;
  flex: 1;
}

.hotkey-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f3f4f6;
}

.hotkey-item:last-child {
  border-bottom: none;
}

.hotkey-desc {
  font-size: 14px;
  color: #374151;
}

.hotkey-key {
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 4px 10px;
  font-family: monospace;
  font-size: 12px;
  color: #374151;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.hotkey-empty {
  text-align: center;
  color: #9ca3af;
  padding: 24px 0;
}

.hotkey-footer {
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
  text-align: center;
  font-size: 13px;
  color: #6b7280;
}

.hotkey-footer kbd {
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  padding: 2px 6px;
  font-family: monospace;
  font-size: 11px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.fade-enter-active .hotkey-panel,
.fade-leave-active .hotkey-panel {
  transition: transform 0.2s ease;
}

.fade-enter-from .hotkey-panel,
.fade-leave-to .hotkey-panel {
  transform: scale(0.95) translateY(-10px);
}
</style>
