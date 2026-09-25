<script setup lang="ts">
</script>

<template>
  <router-view v-slot="{ Component }">
<!--  mode="out-in" 路由页面切换时：旧页面先淡出 → 完全消失 → 新页面再淡入  -->
    <Transition name="page" mode="out-in">
      <component :is="Component" />
    </Transition>
  </router-view>
</template>

<style>
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(4px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
