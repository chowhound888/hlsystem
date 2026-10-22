<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { Activity, ArrowRight, LockKeyhole, User, Eye, EyeOff } from "lucide-vue-next";
import { useAuth } from "../../stores/auth";

const form = reactive({ username: "", password: "" });
const loading = ref(false);
const showPassword = ref(false);
const loginSuccess = ref(false);
const auth = useAuth();
const router = useRouter();
const canvasRef = ref<HTMLCanvasElement>();
let animationId = 0;

// === 粒子动画系统 ===
interface Particle {
  x: number; y: number; vx: number; vy: number;
  size: number; opacity: number; pulse: number; pulseSpeed: number;
}

function initParticles() {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  function resize() {
    canvas!.width = canvas!.offsetWidth * devicePixelRatio;
    canvas!.height = canvas!.offsetHeight * devicePixelRatio;
    ctx!.scale(devicePixelRatio, devicePixelRatio);
  }
  resize();
  window.addEventListener("resize", resize);

  const W = () => canvas!.offsetWidth;
  const H = () => canvas!.offsetHeight;
  const particles: Particle[] = Array.from({ length: 60 }, () => ({
    x: Math.random() * W(),
    y: Math.random() * H(),
    vx: (Math.random() - 0.5) * 0.4,
    vy: (Math.random() - 0.5) * 0.4,
    size: Math.random() * 2.5 + 1,
    opacity: Math.random() * 0.5 + 0.2,
    pulse: Math.random() * Math.PI * 2,
    pulseSpeed: Math.random() * 0.02 + 0.01,
  }));

  function draw() {
    ctx!.clearRect(0, 0, W(), H());
    const w = W(), h = H();

    // 绘制连线
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x;
        const dy = particles[i].y - particles[j].y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 120) {
          ctx!.beginPath();
          ctx!.strokeStyle = `rgba(255,255,255,${0.12 * (1 - dist / 120)})`;
          ctx!.lineWidth = 0.5;
          ctx!.moveTo(particles[i].x, particles[i].y);
          ctx!.lineTo(particles[j].x, particles[j].y);
          ctx!.stroke();
        }
      }
    }

    // 绘制粒子
    for (const p of particles) {
      p.x += p.vx;
      p.y += p.vy;
      p.pulse += p.pulseSpeed;
      if (p.x < 0 || p.x > w) p.vx *= -1;
      if (p.y < 0 || p.y > h) p.vy *= -1;

      const glow = p.opacity + Math.sin(p.pulse) * 0.15;
      ctx!.beginPath();
      ctx!.arc(p.x, p.y, p.size, 0, Math.PI * 2);
      ctx!.fillStyle = `rgba(255,255,255,${glow})`;
      ctx!.fill();

      // 光晕
      ctx!.beginPath();
      ctx!.arc(p.x, p.y, p.size * 3, 0, Math.PI * 2);
      ctx!.fillStyle = `rgba(255,255,255,${glow * 0.1})`;
      ctx!.fill();
    }

    animationId = requestAnimationFrame(draw);
  }
  draw();
}

async function login() {
  if (!form.username || !form.password) return;
  loading.value = true;
  try {
    await auth.login(form.username, form.password);
    loginSuccess.value = true;
    setTimeout(() => router.push("/workbench"), 600);
  } finally {
    loading.value = false;
  }
}

onMounted(initParticles);
onUnmounted(() => cancelAnimationFrame(animationId));
</script>

<template>
  <main class="login" :class="{ 'login-success': loginSuccess }">
    <!-- 左侧品牌区 -->
    <div class="login-brand-area">
      <canvas ref="canvasRef" class="particle-canvas"></canvas>
      <div class="brand-content">
        <div class="brand-icon brand-icon-animate">
          <Activity :size="48" />
        </div>
        <h1 class="brand-title-animate">仁和教学医院</h1>
        <p class="brand-subtitle brand-subtitle-animate">药品管理分系统</p>
        <div class="brand-features brand-features-animate">
          <div class="feature-item" v-for="(f, i) in ['药库管理', '药房发药', '制剂生产', '药品核算']" :key="i" :style="{ animationDelay: `${0.8 + i * 0.1}s` }">
            <span class="feature-dot"></span>
            <span>{{ f }}</span>
          </div>
        </div>
      </div>
      <div class="decoration-circle circle-1"></div>
      <div class="decoration-circle circle-2"></div>
      <div class="decoration-circle circle-3"></div>
    </div>

    <!-- 右侧表单区 -->
    <div class="login-form-area">
      <div class="login-card">
        <div class="login-logo login-logo-animate">
          <div class="login-logo-icon">
            <LockKeyhole :size="24" />
          </div>
          <div>
            <h2>账号登录</h2>
            <p class="login-subtitle">请输入您的账号信息</p>
          </div>
        </div>

        <el-form :model="form" label-position="top" @submit.prevent="login">
          <div class="form-item-animate" style="animation-delay: 0.3s">
            <el-form-item label="用户名" required>
              <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" size="large">
                <template #prefix><User :size="18" /></template>
              </el-input>
            </el-form-item>
          </div>

          <div class="form-item-animate" style="animation-delay: 0.45s">
            <el-form-item label="密码" required>
              <el-input v-model="form.password" :type="showPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" size="large">
                <template #prefix><LockKeyhole :size="18" /></template>
                <template #suffix>
                  <button type="button" class="password-toggle" @click="showPassword = !showPassword">
                    <Eye v-if="!showPassword" :size="18" /><EyeOff v-else :size="18" />
                  </button>
                </template>
              </el-input>
            </el-form-item>
          </div>

          <div class="form-item-animate" style="animation-delay: 0.6s">
            <button type="button" class="login-button-ripple" :disabled="loading" @click="login">
              <span class="btn-content">
                <span v-if="loading" class="btn-spinner"></span>
                <span>{{ loading ? '登录中...' : '登 录' }}</span>
                <ArrowRight :size="18" v-if="!loading" />
              </span>
            </button>
          </div>
        </el-form>

        <div class="login-footer login-footer-animate">
          <p>药事管理工作平台 · 仅供授权人员使用</p>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 480px;
  background: var(--color-bg-page);
  transition: opacity 0.5s ease, transform 0.5s ease;
}
.login-success {
  opacity: 0;
  transform: scale(1.05);
}

/* === 粒子画布 === */
.particle-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}

/* === 左侧品牌区 === */
.login-brand-area {
  background: linear-gradient(135deg, #0d9488 0%, #0f766e 50%, #115e59 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 60px;
  color: white;
  position: relative;
  overflow: hidden;
}

.brand-content {
  position: relative;
  z-index: 1;
  text-align: center;
  max-width: 400px;
}

/* 品牌图标弹入 */
.brand-icon {
  width: 80px; height: 80px;
  background: rgba(255,255,255,0.15);
  border-radius: 20px;
  display: grid; place-items: center;
  margin: 0 auto 32px;
  backdrop-filter: blur(10px);
}
.brand-icon-animate {
  animation: iconBounceIn 0.8s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
@keyframes iconBounceIn {
  0% { opacity: 0; transform: scale(0) rotate(-180deg); }
  100% { opacity: 1; transform: scale(1) rotate(0deg); }
}

/* 标题滑入 */
.login-brand-area h1 {
  font-size: 36px; font-weight: 700; margin-bottom: 12px; color: white;
}
.brand-title-animate {
  animation: slideInUp 0.6s ease-out 0.2s both;
}
.brand-subtitle {
  font-size: 18px; opacity: 0.9; margin-bottom: 48px;
}
.brand-subtitle-animate {
  animation: slideInUp 0.6s ease-out 0.35s both;
}
@keyframes slideInUp {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 特性列表交错入场 */
.brand-features {
  display: flex; flex-wrap: wrap; justify-content: center; gap: 16px 32px;
}
.brand-features-animate .feature-item {
  animation: fadeInScale 0.5s ease-out both;
}
@keyframes fadeInScale {
  from { opacity: 0; transform: scale(0.8) translateY(10px); }
  to { opacity: 0.85; transform: scale(1) translateY(0); }
}
.feature-item {
  display: flex; align-items: center; gap: 8px; font-size: 14px;
}
.feature-dot {
  width: 6px; height: 6px;
  background: rgba(255,255,255,0.6);
  border-radius: 50%;
  animation: dotPulse 2s ease-in-out infinite;
}
@keyframes dotPulse {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.5); opacity: 1; }
}

/* 装饰圆形浮动 */
.decoration-circle {
  position: absolute; border-radius: 50%;
  background: rgba(255,255,255,0.08);
}
.circle-1 {
  width: 600px; height: 600px; top: -200px; left: -200px;
  animation: floatSlow 20s ease-in-out infinite;
}
.circle-2 {
  width: 400px; height: 400px; bottom: -100px; right: -100px;
  animation: floatSlow 15s ease-in-out infinite reverse;
}
.circle-3 {
  width: 200px; height: 200px; top: 50%; right: 10%;
  background: rgba(255,255,255,0.05);
  animation: floatSlow 12s ease-in-out infinite 2s;
}
@keyframes floatSlow {
  0%, 100% { transform: translate(0, 0); }
  33% { transform: translate(30px, -30px); }
  66% { transform: translate(-20px, 20px); }
}

/* === 右侧表单区 === */
.login-form-area {
  display: flex; flex-direction: column; justify-content: center;
  padding: 60px; background: var(--color-bg-card);
}
.login-card { max-width: 380px; width: 100%; margin: 0 auto; }

/* Logo 入场 */
.login-logo {
  display: flex; align-items: center; gap: 16px; margin-bottom: 40px;
}
.login-logo-animate {
  animation: slideInRight 0.6s ease-out 0.1s both;
}
@keyframes slideInRight {
  from { opacity: 0; transform: translateX(30px); }
  to { opacity: 1; transform: translateX(0); }
}
.login-logo-icon {
  width: 48px; height: 48px;
  background: var(--color-primary-lighter);
  border-radius: var(--radius-md);
  display: grid; place-items: center;
  color: var(--color-primary);
}
.login-card h2 { font-size: 24px; color: var(--color-text-primary); margin-bottom: 4px; }
.login-subtitle { font-size: 14px; color: var(--color-text-secondary); margin: 0; }

/* 表单项逐个滑入 */
.form-item-animate {
  animation: formSlideIn 0.5s ease-out both;
}
@keyframes formSlideIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

:deep(.el-form-item) { margin-bottom: 24px; }
:deep(.el-form-item__label) {
  font-size: 14px; font-weight: 500;
  color: var(--color-text-regular); margin-bottom: 8px;
}
:deep(.el-input__wrapper) {
  border-radius: var(--radius-md); padding: 4px 16px;
  box-shadow: 0 0 0 1px var(--color-border) inset;
  transition: all 0.3s ease;
}
:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--color-primary) inset;
}
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 3px var(--color-primary-light) inset, 0 0 0 1px var(--color-primary) inset;
}
:deep(.el-input--large .el-input__inner) { height: 44px; font-size: 15px; }
:deep(.el-input__prefix), :deep(.el-input__suffix) { color: var(--color-text-secondary); }

.password-toggle {
  background: none; border: none; padding: 4px; cursor: pointer;
  color: var(--color-text-secondary); display: flex; align-items: center;
  transition: color 0.2s;
}
.password-toggle:hover { color: var(--color-primary); }

/* === 登录按钮 - 涟漪效果 === */
.login-button-ripple {
  width: 100%; height: 48px; font-size: 16px; font-weight: 600;
  border-radius: var(--radius-md); border: none; cursor: pointer;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: white; position: relative; overflow: hidden;
  transition: all 0.3s ease; margin-top: 8px;
}
.login-button-ripple:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(13, 148, 136, 0.4);
}
.login-button-ripple:active { transform: translateY(0); }
.login-button-ripple:disabled { opacity: 0.7; cursor: not-allowed; transform: none; }
.btn-content {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  position: relative; z-index: 1;
}
.btn-spinner {
  width: 18px; height: 18px; border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white; border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 涟漪 */
.login-button-ripple::after {
  content: ''; position: absolute; inset: 0;
  background: radial-gradient(circle at var(--ripple-x, 50%) var(--ripple-y, 50%), rgba(255,255,255,0.3) 0%, transparent 60%);
  opacity: 0; transition: opacity 0.3s;
}
.login-button-ripple:active::after { opacity: 1; }

/* 底部 */
.login-footer { margin-top: 48px; text-align: center; }
.login-footer p { font-size: 12px; color: var(--color-text-secondary); margin: 0; }
.login-footer-animate { animation: fadeIn 0.6s ease-out 0.8s both; }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

/* === 响应式 === */
@media (max-width: 960px) {
  .login { grid-template-columns: 1fr; }
  .login-brand-area { display: none; }
  .login-form-area { padding: 40px 24px; }
}
@media (max-width: 480px) {
  .login-form-area { padding: 32px 20px; }
  .login-card { max-width: 100%; }
}
</style>
