<script setup lang="ts">
defineProps<{
  count?: number
}>()
</script>

<template>
  <div class="loading-list">
    <div v-for="n in (count || 4)" :key="n" class="skeleton-card">
      <div class="skeleton-card__body">
        <el-skeleton animated>
          <template #template>
            <div class="sk-cat">
              <el-skeleton-item variant="text" style="width: 56px; height: 20px" />
            </div>
            <el-skeleton-item variant="text" style="width: 75%; height: 22px" />
            <el-skeleton-item variant="text" style="width: 100%; height: 16px" />
            <el-skeleton-item variant="text" style="width: 60%; height: 16px" />
            <div class="sk-footer">
              <el-skeleton-item variant="circle" style="width: 26px; height: 26px" />
              <el-skeleton-item variant="text" style="width: 80px; height: 14px" />
              <el-skeleton-item variant="text" style="width: 180px; height: 14px" />
            </div>
          </template>
        </el-skeleton>
      </div>
      <div class="skeleton-card__thumb">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="image" style="width: 100%; height: 100%" />
          </template>
        </el-skeleton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.loading-list {
  display: grid;
  gap: 12px;
}

.skeleton-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 20px;
  padding: 18px;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: var(--tc-shadow-xs);
}

.skeleton-card__body {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.sk-cat {
  margin-bottom: -4px;
}

.sk-footer {
  display: flex;
  align-items: center;
  gap: 10px;
}

.skeleton-card__thumb {
  width: 180px;
  height: 104px;
  border-radius: 4px;
  overflow: hidden;
}

@media (max-width: 640px) {
  .skeleton-card {
    grid-template-columns: 1fr;
  }

  .skeleton-card__thumb {
    width: 100%;
    height: auto;
    aspect-ratio: 16 / 9;
  }
}
</style>
