<script setup lang="ts">
import type { ModelRelease, SimulationMode } from '../model/types'

defineProps<{
  releases: ModelRelease[]
  version: string
  mode: SimulationMode
  seed: number
  loading: boolean
  running: boolean
}>()

const emit = defineEmits<{
  'update:version': [value: string]
  'update:mode': [value: SimulationMode]
  'update:seed': [value: number]
  changeCatalog: []
  run: []
}>()
</script>

<template>
  <section class="run-toolbar" aria-labelledby="run-setup-title">
    <div class="section-heading">
      <div>
        <p class="eyebrow">运行配置</p>
        <h2 id="run-setup-title">选择模型与计算方式</h2>
      </div>
      <p class="section-note">基准模式锁定参考参数；场景模式允许修改并参与本次 Java 计算。</p>
    </div>

    <div class="toolbar-fields">
      <label>
        <span>模型版本</span>
        <select
          :value="version"
          data-testid="model-version"
          @change="emit('update:version', ($event.target as HTMLSelectElement).value); emit('changeCatalog')"
        >
          <option v-for="release in releases" :key="release.version" :value="release.version">
            {{ release.displayName }} · {{ release.outputFieldCount }} 字段
          </option>
        </select>
      </label>

      <fieldset class="mode-switch">
        <legend>运行模式</legend>
        <label>
          <input
            type="radio"
            name="mode"
            value="BASELINE"
            :checked="mode === 'BASELINE'"
            @change="emit('update:mode', 'BASELINE'); emit('changeCatalog')"
          >
          基准复现
        </label>
        <label>
          <input
            type="radio"
            name="mode"
            value="SCENARIO"
            :checked="mode === 'SCENARIO'"
            @change="emit('update:mode', 'SCENARIO'); emit('changeCatalog')"
          >
          场景计算
        </label>
      </fieldset>

      <label>
        <span>随机种子</span>
        <input
          type="number"
          :value="seed"
          min="0"
          step="1"
          @input="emit('update:seed', Number(($event.target as HTMLInputElement).value))"
        >
      </label>

      <button class="primary-action" type="button" :disabled="loading || running || !version" @click="emit('run')">
        <span v-if="running" class="spinner" aria-hidden="true" />
        {{ running ? '正在计算…' : '运行仿真' }}
      </button>
    </div>
  </section>
</template>
