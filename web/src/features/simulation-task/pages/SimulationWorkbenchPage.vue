<script setup lang="ts">
import { createHttpSimulationPlatformAdapter } from '../adapters/httpSimulationPlatformAdapter'
import { useSimulationWorkbench } from '../application/useSimulationWorkbench'
import ModelRunToolbar from '../components/ModelRunToolbar.vue'
import ParameterEditor from '../components/ParameterEditor.vue'
import RunMonitor from '../components/RunMonitor.vue'
import ResultChartGrid from '../components/ResultChartGrid.vue'
import MqttDeliveryPanel from '../components/MqttDeliveryPanel.vue'

const workbench = useSimulationWorkbench(createHttpSimulationPlatformAdapter())

function updateParameter(code: string, value: number) {
  workbench.parameterValues.value = { ...workbench.parameterValues.value, [code]: value }
}
</script>

<template>
  <main>
    <header class="app-header">
      <div>
        <p class="product-label">HVAC SIMULATION PLATFORM</p>
        <h1>Gaia 仿真工作台</h1>
        <p>可复现的模型计算、参数实验与中央空调数据接入。</p>
      </div>
      <div class="header-status">
        <span aria-hidden="true" />
        Java 独立计算链路
      </div>
    </header>

    <div v-if="workbench.error.value" class="error-banner" role="alert">
      <div>
        <strong>{{ workbench.error.value.title }}</strong>
        <p>{{ workbench.error.value.message }}</p>
      </div>
      <button type="button" aria-label="关闭错误提示" @click="workbench.dismissError">×</button>
    </div>

    <ModelRunToolbar
      :releases="workbench.releases.value"
      :version="workbench.selectedVersion.value"
      :mode="workbench.mode.value"
      :seed="workbench.seed.value"
      :loading="workbench.loadingCatalog.value"
      :running="workbench.running.value"
      @update:version="workbench.selectedVersion.value = $event"
      @update:mode="workbench.mode.value = $event"
      @update:seed="workbench.seed.value = $event"
      @change-catalog="workbench.loadCatalog"
      @run="workbench.startRun"
    />

    <div class="workspace-grid">
      <ParameterEditor
        :parameters="workbench.catalog.value?.parameters ?? []"
        :values="workbench.parameterValues.value"
        :editable="workbench.editable.value"
        :loading="workbench.loadingCatalog.value"
        @update:value="updateParameter"
      />
      <div class="side-column">
        <RunMonitor :run="workbench.run.value" />
        <MqttDeliveryPanel
          :enabled="workbench.canDeliverMqtt.value"
          :delivering="workbench.delivering.value"
          :delivery="workbench.delivery.value"
          @deliver="workbench.startDelivery"
        />
      </div>
    </div>

    <ResultChartGrid :result="workbench.series.value" />
  </main>
</template>
