<template>
  <main class="content-area">
    <router-view />
  </main>
</template>

<style scoped>
.content-area {
  flex: 1;
  min-height: 0;
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>
