<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { Activity, ArrowRight, LockKeyhole } from "lucide-vue-next";
import { useAuth } from "../../stores/auth";
const form = reactive({ username: "", password: "" }),
  loading = ref(false),
  auth = useAuth(),
  router = useRouter();
async function login() {
  if (!form.username || !form.password) return;
  loading.value = true;
  try {
    await auth.login(form.username, form.password);
    await router.push("/workbench");
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <main class="login">
    <div class="login-brand">
      <Activity :size="30" /><span>仁和教学医院</span>
    </div>
    <section class="login-form">
      <div class="login-icon"><LockKeyhole :size="26" /></div>
      <h1>药品管理分系统</h1>
      <p class="muted">账号登录</p>
      <el-form :model="form" label-position="top" @submit.prevent="login"
        ><el-form-item label="用户名" required
          ><el-input
            v-model="form.username"
            autocomplete="username"
            size="large" /></el-form-item
        ><el-form-item label="密码" required
          ><el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            size="large" /></el-form-item
        ><el-button
          type="primary"
          native-type="submit"
          size="large"
          :loading="loading"
          style="width: 100%"
          >登录 <ArrowRight :size="16" style="margin-left: 8px" /></el-button
      ></el-form>
    </section>
    <footer>药库 · 药房 · 制剂 · 药品核算</footer>
  </main>
</template>
<style scoped>
.login {
  min-height: 100vh;
  background: #f5f6f8;
  padding: 40px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #15803d;
  font-size: 17px;
  font-weight: 600;
  margin: 35px 0;
}
.login-form {
  width: 400px;
  max-width: 100%;
  padding: 36px;
  background: #fff;
  border: 1px solid #dde1e6;
  border-radius: 6px;
}
.login-form h1 {
  margin-top: 16px;
}
.login-icon {
  color: #2563eb;
}
.login-form p {
  margin: 12px 0 26px;
}
.login footer {
  font-size: 12px;
  color: #69717d;
  margin-top: 30px;
}
</style>
