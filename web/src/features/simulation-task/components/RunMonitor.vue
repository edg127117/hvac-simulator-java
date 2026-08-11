<script setup lang="ts">
import { computed } from 'vue'
import type { RunView } from '../model/types'

const props = defineProps<{ run: RunView | null }>()

const progress = computed(() => {
  if (!props.run || props.run.totalSteps === 0) return 0
  return Math.round((props.run.completedSteps / props.run.totalSteps) * 100)
})

const statusText = computed(() => ({
  QUEUED: '已排队', RUNNING: '计算中', COMPLETED: '已完成', FAILED: '失败',
}[props.run?.status ?? 'QUEUED']))
</script>

<template>
  <section class="monitor-panel" aria-labelledby="monitor-title" aria-live="polite">
    <div class="section-heading compact-heading">
      <div>
        <p class="eyebrow">运行监控</p>
        <h2 id="monitor-title">任务状态</h2>
      </div>
      <span v-if="run" class="status-chip" :data-status="run.status">{{ statusText }}</span>
    </div>
    <div v-if="!run" class="empty-state">配置参数后运行仿真，这里将显示进度。</div>
    <template v-else>
      <div class="progress-track" :aria-label="`计算进度 ${progress}%`">
        <span :style="{ width: `${progress}%` }" />
      </div>
      <dl class="monitor-metrics">
        <div><dt>进度</dt><dd>{{ progress }}%</dd></div>
        <div><dt>已完成时间步</dt><dd>{{ run.completedSteps.toLocaleString() }} / {{ run.totalSteps.toLocaleString() }}</dd></div>
        <div><dt>当前仿真时间</dt><dd>{{ run.simulationTime?.replace('T', ' ') ?? '—' }}</dd></div>
        <div><dt>任务编号</dt><dd class="run-id">{{ run.runId }}</dd></div>
      </dl>
    </template>
  </section>
</template>
