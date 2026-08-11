<script setup lang="ts">
import { init, use, type ECharts } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { DataZoomComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ResultSeriesGroup } from '../../features/simulation-task/model/types'

const props = defineProps<{ timestamps: string[]; group: ResultSeriesGroup }>()
const host = ref<HTMLDivElement | null>(null)
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

use([LineChart, DataZoomComponent, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

function render() {
  chart?.setOption({
    animation: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    color: ['#0b7189', '#e2762e', '#4f7c5d', '#845ec2', '#bf4d5d'],
    tooltip: { trigger: 'axis', confine: true },
    legend: { type: 'scroll', top: 0, textStyle: { color: '#43525b' } },
    grid: { top: 42, right: 24, bottom: 56, left: 64 },
    xAxis: {
      type: 'category', boundaryGap: false, data: props.timestamps,
      axisLabel: { hideOverlap: true, color: '#65737a' },
      axisLine: { lineStyle: { color: '#c9d4d8' } },
    },
    yAxis: {
      type: 'value', name: props.group.unit, scale: true,
      nameTextStyle: { color: '#65737a' }, axisLabel: { color: '#65737a' },
      splitLine: { lineStyle: { color: '#e9eff1' } },
    },
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 18, bottom: 10 }],
    series: props.group.series.map((item) => ({
      name: item.label, type: 'line', data: item.values, showSymbol: false,
      sampling: 'lttb', lineStyle: { width: 1.6 }, emphasis: { focus: 'series' },
    })),
  }, true)
}

onMounted(() => {
  if (!host.value) return
  chart = init(host.value)
  render()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(host.value)
})

watch(() => [props.timestamps, props.group], render, { deep: true })

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="host" class="time-series-chart" role="img" :aria-label="`${group.title}时间序列图`" />
</template>

<style scoped>
.time-series-chart { width: 100%; height: 310px; }
</style>
