<script setup lang="ts">
import type { TimelineItem } from '@/types/common'
import { formatDateTime } from '@/utils/format'

defineProps<{
  history: TimelineItem[]
}>()
</script>

<template>
  <el-empty v-if="!history.length" description="暂无状态流转记录" />
  <el-timeline v-else>
    <el-timeline-item
      v-for="item in history"
      :key="`${item.title}-${item.time}`"
      :timestamp="formatDateTime(item.time)"
      placement="top"
    >
      <div class="timeline-card">
        <div class="timeline-card__title">{{ item.title }}</div>
        <div class="timeline-card__meta">
          <span>{{ item.owner || '--' }}</span>
        </div>
        <p v-if="item.description" class="timeline-card__desc">{{ item.description }}</p>
      </div>
    </el-timeline-item>
  </el-timeline>
</template>

<style scoped>
.timeline-card__title {
  font-weight: 600;
}

.timeline-card__meta,
.timeline-card__desc {
  margin-top: 6px;
  color: var(--plm-color-text-secondary);
}

.timeline-card__desc {
  margin-bottom: 0;
  line-height: 1.6;
}
</style>
