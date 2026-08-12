<script setup lang="ts">
import { computed } from 'vue'
import type { ModelParameter } from '../model/types'

const props = defineProps<{
  parameters: ModelParameter[]
  values: Record<string, number>
  editable: boolean
  loading: boolean
  versionDisplayName: string
}>()

const emit = defineEmits<{ 'update:value': [code: string, value: number] }>()

function groupParameters(parameters: ModelParameter[]) {
  return Map.groupBy(parameters, (parameter) => parameter.group)
}

const parameterSections = computed(() => [
  {
    scope: 'COMMON',
    title: '公共参数',
    items: props.parameters.filter((parameter) => parameter.scope === 'COMMON'),
  },
  {
    scope: 'VERSION_SPECIFIC',
    title: `${props.versionDisplayName} 版本专属参数`,
    items: props.parameters.filter((parameter) => parameter.scope === 'VERSION_SPECIFIC'),
  },
])
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
    <div v-else class="parameter-sections">
      <section v-for="section in parameterSections" :key="section.scope" class="parameter-scope">
        <div class="parameter-scope-heading">
          <h3>{{ section.title }}</h3>
          <span>{{ section.items.length }} 项</span>
        </div>
        <p v-if="section.items.length === 0" class="parameter-empty">当前版本暂无专属参数</p>
        <div v-else class="parameter-groups">
          <fieldset v-for="[group, items] in groupParameters(section.items)" :key="group">
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
    </div>
  </section>
</template>
