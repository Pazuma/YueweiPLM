<template>
  <div class="layout-shell">
    <Sidebar />
    <div class="layout-shell__main">
      <Navbar />
      <ContentArea />
    </div>
  </div>
</template>

<script setup lang="ts">
import ContentArea from './ContentArea.vue'
import Navbar from './Navbar.vue'
import Sidebar from './Sidebar.vue'
</script>

<style scoped>
.layout-shell {
  display: flex;
  min-height: 100vh;
}

.layout-shell__main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}
</style>
