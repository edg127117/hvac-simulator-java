<script setup lang="ts">
import type { SeriesResponse } from '../model/types'
import TimeSeriesChart from '../../../shared/chart/TimeSeriesChart.vue'

defineProps<{ result: SeriesResponse | null }>()
</script>

<template>
  <section class="results-section" aria-labelledby="results-title">
    <div class="section-heading">
      <div>
        <p class="eyebrow">计算结果</p>
        <h2 id="results-title">五联时间序列</h2>
      </div>
      <p class="section-note">温度、负荷、功率、理论/测量 COP 与水温均来自同一次 Java 仿真。</p>
    </div>
    <div v-if="!result" class="empty-state chart-empty">运行完成后显示真实结果曲线。</div>
    <div v-else class="chart-grid">
      <article v-for="group in result.groups" :key="group.code" class="chart-panel">
        <h3>{{ group.title }}</h3>
        <TimeSeriesChart :timestamps="result.timestamps" :group="group" />
      </article>
    </div>
  </section>
</template>
