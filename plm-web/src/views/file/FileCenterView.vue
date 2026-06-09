<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getFileSections } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import type { SearchField } from '@/types/common'
import type { FileProjectGroup, FileSection } from '@/types/foundation'
import { formatDate } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const sections = ref<FileSection[]>([])
const activeSection = ref<'product_files' | 'variant_files'>('product_files')
const expandedGroups = ref<string[]>([])
const keyword = ref('')

const searchFields: SearchField[] = [{ prop: 'keyword', label: '文件搜索', type: 'input', placeholder: '文件名 / 产品名 / 编码' }]

const currentSection = computed(() => sections.value.find((item) => item.key === activeSection.value) || sections.value[0] || null)

const filteredGroups = computed(() => {
  const source = currentSection.value?.groups || []
  const search = keyword.value.trim().toLowerCase()
  if (!search) return source

  return source.filter((group) => {
    const groupMatched =
      group.projectName.toLowerCase().includes(search) ||
      group.productCode.toLowerCase().includes(search)
    const fileMatched = group.files.some((file) => file.fileName.toLowerCase().includes(search) || file.category.toLowerCase().includes(search))
    return groupMatched || fileMatched
  })
})

const metrics = computed(() => {
  const allGroups = sections.value.flatMap((item) => item.groups)
  const allFiles = allGroups.flatMap((item) => item.files)
  const productFiles = sections.value.find((item) => item.key === 'product_files')?.groups.flatMap((item) => item.files).length || 0
  const variantFiles = sections.value.find((item) => item.key === 'variant_files')?.groups.flatMap((item) => item.files).length || 0
  return [
    { label: '文件总数', value: allFiles.length, hint: '按产品线和新型号分层归档' },
    { label: '产品文件', value: productFiles, hint: '新产品线完整资料' },
    { label: '新型号文件', value: variantFiles, hint: '只保留差异文件' },
    { label: '项目分组', value: allGroups.length, hint: '同一项目文件集中查看' }
  ]
})

function isExpanded(groupId: string) {
  return expandedGroups.value.includes(groupId)
}

function toggleGroup(group: FileProjectGroup) {
  if (isExpanded(group.groupId)) {
    expandedGroups.value = expandedGroups.value.filter((item) => item !== group.groupId)
    return
  }
  expandedGroups.value = [group.groupId]
}

function openProduct(productId: number) {
  router.push(`/products/${productId}`)
}

async function loadData() {
  loading.value = true
  try {
    sections.value = await getFileSections()
    expandedGroups.value = sections.value[0]?.groups[0] ? [sections.value[0].groups[0].groupId] : []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer title="文件管理" description="文件按“产品文件 / 新型号文件”分层，再按项目归堆。先看项目，再展开具体文件。">
    <section class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card">
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value">{{ metric.value }}</p>
        <span class="metric-card__trend">{{ metric.hint }}</span>
      </div>
    </section>

    <SearchBar
      :fields="searchFields"
      :model-value="{ keyword }"
      @search="(value) => (keyword = String(value.keyword || ''))"
      @reset="keyword = ''"
    />

    <section class="split-grid file-layout" v-loading="loading">
      <article class="page-panel file-nav-panel">
        <h3 class="section-title">文件分类</h3>
        <div class="page-stack">
          <button
            v-for="section in sections"
            :key="section.key"
            class="section-trigger"
            :class="{ 'is-active': activeSection === section.key }"
            type="button"
            @click="activeSection = section.key"
          >
            <strong>{{ section.title }}</strong>
            <span class="subtle-text">{{ section.description }}</span>
          </button>
        </div>
      </article>

      <article class="page-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">{{ currentSection?.title || '文件列表' }}</h3>
            <p class="page-panel-desc">{{ currentSection?.description }}</p>
          </div>
          <el-tag effect="light">{{ filteredGroups.length }} 个项目组</el-tag>
        </div>

        <div class="page-stack">
          <section v-for="group in filteredGroups" :key="group.groupId" class="group-card">
            <button class="group-card__header" type="button" @click="toggleGroup(group)">
              <div class="group-card__meta">
                <strong>{{ group.projectName }}</strong>
                <span class="subtle-text">{{ group.productCode }} / {{ group.owner }} / 更新于 {{ formatDate(group.updatedAt) }}</span>
              </div>
              <div class="group-card__actions">
                <span class="subtle-text">{{ group.files.length }} 个文件</span>
                <el-button link type="primary" @click.stop="openProduct(group.productId)">进入产品</el-button>
              </div>
            </button>

            <div v-if="isExpanded(group.groupId)" class="group-card__body">
              <el-table :data="group.files" border stripe>
                <el-table-column prop="fileName" label="文件名称" min-width="260" />
                <el-table-column prop="category" label="类别" min-width="140" />
                <el-table-column prop="owner" label="上传人" width="120" />
                <el-table-column prop="versionNo" label="版本" width="100" />
                <el-table-column label="上传时间" width="140">
                  <template #default="{ row }">{{ formatDate(row.uploadedAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="120">
                  <template #default>
                    <el-button link type="primary">查看</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </section>
        </div>
      </article>
    </section>
  </PageContainer>
</template>

<style scoped>
.file-layout {
  grid-template-columns: 280px minmax(0, 1fr);
}

.file-nav-panel {
  align-self: start;
}

.section-trigger {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.section-trigger.is-active {
  border-color: var(--plm-color-primary);
  background: #f8fbff;
}

.group-card {
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  overflow: hidden;
  background: #fff;
}

.group-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 14px 16px;
  border: 0;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.group-card__meta,
.group-card__actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.group-card__actions {
  align-items: flex-end;
}

.group-card__body {
  padding: 0 16px 16px;
}

@media (max-width: 1200px) {
  .file-layout {
    grid-template-columns: 1fr;
  }
}
</style>
