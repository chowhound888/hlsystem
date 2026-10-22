<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Home, ChevronRight } from "lucide-vue-next";

const route = useRoute();
const router = useRouter();

interface BreadcrumbItem {
  title: string;
  path?: string;
  to?: any;
}

// 根据路由生成面包屑
const breadcrumbs = computed<BreadcrumbItem[]>(() => {
  const items: BreadcrumbItem[] = [];
  
  // 首页
  if (route.path !== "/workbench") {
    items.push({ title: "工作台", path: "/workbench" });
  }
  
  // 根据路径生成层级
  const pathParts = route.path.split("/").filter(Boolean);
  let currentPath = "";
  
  for (let i = 0; i < pathParts.length; i++) {
    currentPath += "/" + pathParts[i];
    const matched = route.matched.find((r) => r.path === currentPath);
    
    if (matched?.meta?.title) {
      items.push({
        title: matched.meta.title as string,
        path: i < pathParts.length - 1 ? currentPath : undefined,
      });
    }
  }
  
  return items;
});

function navigateTo(path: string) {
  router.push(path);
}
</script>

<template>
  <nav class="breadcrumb" aria-label="面包屑导航">
    <ol class="breadcrumb-list">
      <li class="breadcrumb-item">
        <button 
          class="breadcrumb-link home-link"
          @click="navigateTo('/workbench')"
          aria-label="返回首页"
        >
          <Home :size="14" />
        </button>
      </li>
      
      <template v-for="(item, index) in breadcrumbs" :key="index">
        <li class="breadcrumb-separator" aria-hidden="true">
          <ChevronRight :size="12" />
        </li>
        <li class="breadcrumb-item">
          <button
            v-if="item.path && index < breadcrumbs.length - 1"
            class="breadcrumb-link"
            @click="navigateTo(item.path)"
          >
            {{ item.title }}
          </button>
          <span
            v-else
            class="breadcrumb-current"
            aria-current="page"
          >
            {{ item.title }}
          </span>
        </li>
      </template>
    </ol>
  </nav>
</template>

<style scoped>
.breadcrumb {
  margin-bottom: 16px;
}

.breadcrumb-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
}

.breadcrumb-separator {
  display: flex;
  align-items: center;
  color: var(--color-text-placeholder);
  margin: 0 2px;
}

.breadcrumb-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.breadcrumb-link:hover {
  color: var(--color-primary);
  background: var(--color-bg-hover);
}

.home-link {
  padding: 6px;
}

.breadcrumb-current {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  padding: 4px 8px;
}
</style>
