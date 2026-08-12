<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { useFixedTableHeight } from '@/composables/useFixedTableHeight'

const props = withDefaults(defineProps<{
  bottomOffset?: number
  compact?: boolean
  refreshKey?: unknown
}>(), {
  bottomOffset: 24,
  compact: false,
  refreshKey: undefined
})

const viewportRef = ref<HTMLElement | null>(null)
const { tableHeight, refreshAfterRender } = useFixedTableHeight(viewportRef, props.bottomOffset)
const effectiveTableHeight = computed(() => props.compact ? Math.min(tableHeight.value, 560) : tableHeight.value)

watch(() => props.refreshKey, refreshAfterRender)
</script>

<template>
  <section
    ref="viewportRef"
    class="plm-fixed-table-viewport"
    :class="{ 'plm-fixed-table-viewport--compact': compact }"
    :style="{ '--plm-fixed-table-height': `${effectiveTableHeight}px` }"
  >
    <slot :table-height="effectiveTableHeight" />
  </section>
</template>

<style scoped>
.plm-fixed-table-viewport {
  min-width: 0;
  height: var(--plm-fixed-table-height);
  min-height: 320px;
  max-height: 760px;
}

.plm-fixed-table-viewport--compact {
  min-height: 260px;
  max-height: 560px;
}

.plm-fixed-table-viewport :deep(.el-table) {
  height: 100%;
}

.plm-fixed-table-viewport :deep(.el-table__inner-wrapper::before) {
  z-index: 1;
}
</style>
