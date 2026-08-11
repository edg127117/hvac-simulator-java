<script setup lang="ts">
import type { ModelParameter } from '../model/types'

defineProps<{
  parameters: ModelParameter[]
  values: Record<string, number>
  editable: boolean
  loading: boolean
}>()

const emit = defineEmits<{ 'update:value': [code: string, value: number] }>()

function groupParameters(parameters: ModelParameter[]) {
  return Map.groupBy(parameters, (parameter) => parameter.group)
}
</script>

<template>
  <section class="parameter-panel" aria-labelledby="parameters-title">
    <div class="section-heading compact-heading">
      <div>
        <p class="eyebrow">模型输入</p>
        <h2 id="parameters-title">真实默认参数</h2>
      </div>
      <span class="mode-badge" :class="{ editable }">{{ editable ? '可编辑' : '基准只读' }}</span>
    </div>

    <div v-if="loading" class="empty-state">正在读取模型参数…</div>
    <div v-else class="parameter-groups">
      <fieldset v-for="[group, items] in groupParameters(parameters)" :key="group">
        <legend>{{ group }}</legend>
        <div class="parameter-grid">
          <label v-for="parameter in items" :key="parameter.code">
            <span>{{ parameter.label }}</span>
            <div class="input-with-unit">
              <input
                type="number"
                :value="values[parameter.code]"
                :min="parameter.minimum"
                :max="parameter.maximum"
                :step="parameter.valueType === 'INTEGER' ? 1 : 'any'"
                :disabled="!editable || !parameter.editable"
                :aria-describedby="`${parameter.code}-range`"
                @input="emit('update:value', parameter.code, Number(($event.target as HTMLInputElement).value))"
              >
              <span>{{ parameter.unit || '—' }}</span>
            </div>
            <small :id="`${parameter.code}-range`">
              {{ parameter.minimum }} – {{ parameter.maximum }}
            </small>
          </label>
        </div>
      </fieldset>
    </div>
  </section>
</template>
