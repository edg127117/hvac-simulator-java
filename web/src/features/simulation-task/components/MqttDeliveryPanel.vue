<script setup lang="ts">
import { reactive } from 'vue'
import type { DeliveryView } from '../model/types'

defineProps<{ enabled: boolean; delivering: boolean; delivery: DeliveryView | null }>()
const emit = defineEmits<{
  deliver: [input: { fromStep: number; toStep: number; timeMode: 'ORIGINAL' | 'REBASE_TO_NOW'; buildingId: string; deviceId: string }]
}>()

const form = reactive({
  fromStep: 0,
  toStep: 59,
  timeMode: 'REBASE_TO_NOW' as 'ORIGINAL' | 'REBASE_TO_NOW',
  buildingId: 'BLD001',
  deviceId: 'WCR1',
})
</script>

<template>
  <section class="mqtt-panel" aria-labelledby="mqtt-title">
    <div class="section-heading compact-heading">
      <div>
        <p class="eyebrow">平台接入</p>
        <h2 id="mqtt-title">中央空调 MQTT 投递</h2>
      </div>
      <span class="protocol-mark">QoS 1</span>
    </div>
    <p class="mqtt-explanation">
      仅发送 Gaia 1.1 可提供的冷冻水进/出水温、流量与功率；中央平台据此计算 WCR_COP。
    </p>
    <div class="mqtt-form">
      <label><span>建筑</span><input v-model.trim="form.buildingId" :disabled="!enabled || delivering"></label>
      <label><span>设备</span><input v-model.trim="form.deviceId" :disabled="!enabled || delivering"></label>
      <label><span>起始步</span><input v-model.number="form.fromStep" type="number" min="0" max="10079" :disabled="!enabled || delivering"></label>
      <label><span>结束步</span><input v-model.number="form.toStep" type="number" min="0" max="10079" :disabled="!enabled || delivering"></label>
      <label>
        <span>时间模式</span>
        <select v-model="form.timeMode" :disabled="!enabled || delivering">
          <option value="REBASE_TO_NOW">平移到当前时间</option>
          <option value="ORIGINAL">保留 2024 原始时间</option>
        </select>
      </label>
      <button class="secondary-action" type="button" :disabled="!enabled || delivering" @click="emit('deliver', { ...form })">
        {{ delivering ? '正在投递…' : '发送到中央平台' }}
      </button>
    </div>
    <p v-if="!enabled" class="inline-hint">请先完成一次 Gaia 1.1 仿真。</p>
    <dl v-if="delivery" class="delivery-summary" aria-live="polite">
      <div><dt>状态</dt><dd>{{ delivery.status }}</dd></div>
      <div><dt>成功</dt><dd>{{ delivery.successfulMessages }} / {{ delivery.totalMessages }}</dd></div>
      <div><dt>失败</dt><dd>{{ delivery.failedMessages }}</dd></div>
      <div v-if="delivery.firstError"><dt>首个错误</dt><dd>{{ delivery.firstError }}</dd></div>
    </dl>
  </section>
</template>
