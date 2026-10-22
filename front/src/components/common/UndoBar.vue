<script setup lang="ts">
import { computed } from "vue";
import { useFeedback } from "../../composables/useFeedback";
import { Undo2 } from "lucide-vue-next";

const { getUndoState, performUndo } = useFeedback();
const undoState = getUndoState();

const isVisible = computed(() => undoState.value !== null);
</script>

<template>
  <Transition name="slide-up">
    <div v-if="isVisible && undoState" class="undo-bar">
      <div class="undo-content">
        <span class="undo-message">{{ undoState.message }}</span>
        <button class="undo-button" @click="performUndo">
          <Undo2 :size="14" />
          撤销
        </button>
        <span class="undo-countdown">{{ undoState.countdown }}s</span>
      </div>
      <div class="undo-progress" :style="{ width: `${(undoState.countdown / 5) * 100}%` }"></div>
    </div>
  </Transition>
</template>

<style scoped>
.undo-bar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  background: #323232;
  color: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 2000;
  overflow: hidden;
  min-width: 300px;
}

.undo-content {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
}

.undo-message {
  font-size: 14px;
  flex: 1;
}

.undo-button {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: #409eff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}

.undo-button:hover {
  background: rgba(64, 158, 255, 0.1);
}

.undo-countdown {
  font-size: 12px;
  color: #909399;
  min-width: 24px;
  text-align: right;
}

.undo-progress {
  height: 3px;
  background: #409eff;
  transition: width 1s linear;
}

/* 进入/离开动画 */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translate(-50%, 20px);
}
</style>
