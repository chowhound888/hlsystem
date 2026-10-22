<script setup lang="ts">
// App.vue 不需要页面过渡，过渡放在 MainLayout 内部处理
// 这样登录页 → 主页的切换由路由守卫控制，主页内部的切换由 MainLayout 控制
</script>

<template>
  <router-view />
</template>
