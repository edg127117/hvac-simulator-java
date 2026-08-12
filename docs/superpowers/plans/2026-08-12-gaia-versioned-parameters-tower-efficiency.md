# Gaia 版本化参数目录与冷却塔效率接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Gaia 1.0/1.1 分别发布带公共/版本专属标记的独立参数目录，并把 Gaia 1.1 真实冷却塔测点通过可选 MQTT 目标接入中央平台 `TOWER_EFF` 趋势链路。

**Architecture:** 引擎层用 `ParameterScope` 和每版本独立构造函数冻结参数归属；服务端保持现有参数 API 形状并新增字段，MQTT 发送用 `CentralHvacMetricTarget` 选择 WCR 与冷却塔映射。Vue 按参数归属分区并显式选择发送指标；中央平台代码不修改，只由验收脚本验证既有测点别名、公式、趋势和页面链路。

**Tech Stack:** Java 21、Maven、JUnit 5、Spring Boot、Vue 3、TypeScript、Vitest、Playwright、Paho MQTT、PowerShell。

## Global Constraints

- 不修改 Gaia 1.0/1.1 公式、随机消费顺序、时间语义、17/30 字段输出和冻结参考资产。
- `COMMON` 只表示跨版本代码、单位和业务语义一致；每个版本仍拥有独立描述符列表和默认值来源。
- 参数 API 只新增 `scope`，不删除路径或既有字段。
- MQTT 请求缺失 `targets` 时继续只发送 `WCR_COP` 四测点；页面新请求默认显式选择 `WCR_COP` 与 `TOWER_EFF`。
- 冷却塔只映射 Gaia 1.1 已有真实量，不生成水泵压力、AHU 总压或其他虚假测点。
- 冷却塔停止时间步不发送冷却塔测点；异常测量值不由适配器裁剪或修正。
- 不修改 `iot-platform-demo` 和通用自由拓扑设计；本地完整软件栈验收不等于真实现场验收。

---

## File Structure

- `engine/.../release/ParameterScope.java`：参数归属枚举。
- `engine/.../release/ModelParameterDescriptor.java`：在稳定参数合同中携带归属。
- `engine/.../release/ModelReleaseCatalog.java`：分别构造 Gaia 1.0 与 Gaia 1.1 参数目录并应用覆盖。
- `server/.../api/dto/ModelReleaseDtos.java`、`ModelReleaseService.java`：向 API 暴露参数归属。
- `server/.../delivery/CentralHvacMetricTarget.java`：中央空调可发送指标集合。
- `server/.../delivery/CentralHvacPointMapper.java`：分别映射 WCR_COP 和 TOWER_EFF 所需测点。
- `server/.../api/dto/MqttDeliveryDtos.java`、`MqttDeliveryService.java`：兼容解析目标并按实际测点数发送。
- `web/.../model/types.ts`、`ParameterEditor.vue`：公共/专属参数展示合同与组件。
- `web/.../MqttDeliveryPanel.vue`、端口与 composable：双指标发送选择和冷却塔设备编号。
- `scripts/Verify-Gaia11CentralHvacTowerEfficiency.ps1`：中央平台冷却塔效率本地完整联调验收。
- `PROJECT_GUIDE.md`、`PROJECT_STATUS.md`：只记录本次实际完成与未验证边界。

### Task 1: 引擎发布每版本独立参数目录

**Files:**
- Create: `engine/src/main/java/com/hvac/simulator/release/ParameterScope.java`
- Modify: `engine/src/main/java/com/hvac/simulator/release/ModelParameterDescriptor.java`
- Modify: `engine/src/main/java/com/hvac/simulator/release/ModelReleaseCatalog.java`
- Test: `engine/src/test/java/com/hvac/simulator/release/ModelReleaseCatalogTest.java`

**Interfaces:**
- Produces: `enum ParameterScope { COMMON, VERSION_SPECIFIC }`。
- Produces: `ModelParameterDescriptor.scope(): ParameterScope`。
- Preserves: `ModelReleaseCatalog.applyOverrides(ModelVersion, Map<String, Double>): ModelParameterSnapshot`。

- [ ] **Step 1: 写失败测试，锁定目录隔离与归属**

```java
assertTrue(gaia10.parameters().stream().allMatch(p -> p.scope() == ParameterScope.COMMON));
assertEquals(19, gaia11.parameters().stream()
        .filter(p -> p.scope() == ParameterScope.COMMON).count());
assertEquals(4, gaia11.parameters().stream()
        .filter(p -> p.scope() == ParameterScope.VERSION_SPECIFIC).count());
assertNotSame(gaia10.parameters(), gaia11.parameters());
```

- [ ] **Step 2: 运行定向测试并确认因 `scope()` 不存在而失败**

Run: `./mvnw.cmd -pl engine -Dtest=ModelReleaseCatalogTest test`

Expected: 编译失败，提示 `ParameterScope` 或 `scope()` 未定义。

- [ ] **Step 3: 实现参数归属和每版本独立构造**

```java
public enum ParameterScope {
    COMMON,
    VERSION_SPECIFIC
}
```

`ModelParameterDescriptor` 在 `editable` 前增加 `ParameterScope scope` 并校验非空。`ModelReleaseCatalog` 使用：

```java
List<ModelParameterDescriptor> gaia10 = gaia10Parameters();
List<ModelParameterDescriptor> gaia11 = gaia11Parameters();
releases = Map.of(
        ModelVersion.GAIA_1_0, new ModelReleaseDescriptor(..., gaia10),
        ModelVersion.GAIA_1_1, new ModelReleaseDescriptor(..., gaia11));
```

两个方法分别创建列表；物理参数工厂传入 `ParameterScope.COMMON`，测量参数传入 `ParameterScope.VERSION_SPECIFIC`。不得共享同一个 `physical` 列表对象。

- [ ] **Step 4: 验证归属、跨版本拒绝和参数实际参与计算**

Run: `./mvnw.cmd -pl engine -Dtest=ModelReleaseCatalogTest test`

Expected: `ModelReleaseCatalogTest` 全部通过。

- [ ] **Step 5: 运行模型敏感回归**

Run: `./mvnw.cmd -pl engine -Dtest=Gaia11ParityTest,GaiaParityTest,ModelReleaseCatalogTest test`

Expected: Gaia 1.1 全部时间步 30 字段一致、Gaia 1.0 基准回归和参数目录测试通过。

- [ ] **Step 6: 提交引擎参数目录**

```powershell
git add -- engine/src/main/java/com/hvac/simulator/release/ParameterScope.java `
  engine/src/main/java/com/hvac/simulator/release/ModelParameterDescriptor.java `
  engine/src/main/java/com/hvac/simulator/release/ModelReleaseCatalog.java `
  engine/src/test/java/com/hvac/simulator/release/ModelReleaseCatalogTest.java
git commit -m "feat(model): 隔离 Gaia 版本参数目录"
```

### Task 2: API 暴露公共与版本专属参数

**Files:**
- Modify: `server/src/main/java/com/hvac/simulator/server/api/dto/ModelReleaseDtos.java`
- Modify: `server/src/main/java/com/hvac/simulator/server/application/ModelReleaseService.java`
- Modify: `server/src/test/java/com/hvac/simulator/server/SimulationPlatformApiTest.java`

**Interfaces:**
- Consumes: `ModelParameterDescriptor.scope()`。
- Produces: 参数 JSON 字段 `scope: "COMMON"|"VERSION_SPECIFIC"`。

- [ ] **Step 1: 写失败 API 断言**

```java
.andExpect(jsonPath("$.parameters[?(@.code == 'hvac.coolingSetpointC')].scope")
        .value("COMMON"))
.andExpect(jsonPath("$.parameters[?(@.code == 'measurement.sensorBias')].scope")
        .value("VERSION_SPECIFIC"));
```

另请求 Gaia 1.0，断言不存在 `VERSION_SPECIFIC` 参数。

- [ ] **Step 2: 运行失败测试**

Run: `./mvnw.cmd -pl server -Dtest=SimulationPlatformApiTest test`

Expected: JSON 路径 `scope` 不存在。

- [ ] **Step 3: 扩展 DTO 和映射**

```java
public record Parameter(
        String code, String label, String group, String unit,
        ParameterValueType valueType, double defaultValue, double minimum,
        double maximum, ParameterScope scope, boolean editable, String readOnlyReason) {}
```

`ModelReleaseService.toDto` 传入 `parameter.scope()`；不改变模式相关可编辑规则。

- [ ] **Step 4: 运行 API 与完整 server 定向测试**

Run: `./mvnw.cmd -pl server -Dtest=SimulationPlatformApiTest,SimulationPlatformApplicationTest test`

Expected: 参数 API、上下文启动测试通过。

- [ ] **Step 5: 提交 API 合同**

```powershell
git add -- server/src/main/java/com/hvac/simulator/server/api/dto/ModelReleaseDtos.java `
  server/src/main/java/com/hvac/simulator/server/application/ModelReleaseService.java `
  server/src/test/java/com/hvac/simulator/server/SimulationPlatformApiTest.java
git commit -m "feat(server): 暴露版本参数归属"
```

### Task 3: Vue 展示公共参数和版本专属参数

**Files:**
- Modify: `web/src/features/simulation-task/model/types.ts`
- Modify: `web/src/features/simulation-task/components/ParameterEditor.vue`
- Modify: `web/src/features/simulation-task/components/ParameterEditor.test.ts`
- Modify: `web/src/features/simulation-task/application/useSimulationWorkbench.test.ts`
- Modify: `web/tests/e2e/simulation-workbench.spec.ts`
- Modify: `web/src/styles/main.css`

**Interfaces:**
- Consumes: `ModelParameter.scope: 'COMMON'|'VERSION_SPECIFIC'`。
- Preserves: `update:value(code: string, value: number)`。

- [ ] **Step 1: 更新测试数据并写失败展示断言**

```ts
const common = { ...parameter, scope: 'COMMON' as const }
const specific = {
  ...parameter,
  code: 'measurement.sensorBias',
  label: '传感器统一偏差',
  group: '测量',
  scope: 'VERSION_SPECIFIC' as const,
}
expect(wrapper.text()).toContain('公共参数')
expect(wrapper.text()).toContain('Gaia 1.1 版本专属参数')
```

Gaia 1.0 仅传公共参数时断言“当前版本暂无专属参数”。composable 测试让两个版本返回不同默认值，切换后断言旧版本值被整体替换。

- [ ] **Step 2: 运行失败测试**

Run: `pnpm run test:run -- src/features/simulation-task/components/ParameterEditor.test.ts src/features/simulation-task/application/useSimulationWorkbench.test.ts`

Working directory: `web`

Expected: 缺少归属标题或 TypeScript `scope` 字段失败。

- [ ] **Step 3: 实现分区展示**

```ts
export type ParameterScope = 'COMMON' | 'VERSION_SPECIFIC'
```

`ParameterEditor.vue` 增加 `versionDisplayName` 属性，并按下列结构渲染：

```vue
<section v-for="section in parameterSections" :key="section.scope">
  <h3>{{ section.title }}</h3>
  <p v-if="section.items.length === 0">当前版本暂无专属参数</p>
  <fieldset v-for="[group, items] in groupParameters(section.items)" :key="group">...</fieldset>
</section>
```

页面传入 `workbench.catalog.value?.displayName ?? '当前模型'`；使用小范围 CSS 区分归属标题，不重做整体视觉。

- [ ] **Step 4: 更新 E2E 断言**

Playwright 先确认 Gaia 1.1 有“版本专属参数”，切换 Gaia 1.0 后确认空状态并且测量参数消失，再切回 Gaia 1.1 继续现有场景运行。

- [ ] **Step 5: 运行前端单元验证**

Run: `pnpm run check`

Run: `pnpm run test:run`

Working directory: `web`

Expected: 类型检查和 Vitest 全部通过。

- [ ] **Step 6: 提交前端参数展示**

```powershell
git add -- web/src/features/simulation-task/model/types.ts `
  web/src/features/simulation-task/components/ParameterEditor.vue `
  web/src/features/simulation-task/components/ParameterEditor.test.ts `
  web/src/features/simulation-task/application/useSimulationWorkbench.test.ts `
  web/src/features/simulation-task/pages/SimulationWorkbenchPage.vue `
  web/src/styles/main.css web/tests/e2e/simulation-workbench.spec.ts
git commit -m "feat(web): 标注公共与版本专属参数"
```

### Task 4: 冷却塔测点映射与可选 MQTT 目标

**Files:**
- Create: `server/src/main/java/com/hvac/simulator/server/delivery/CentralHvacMetricTarget.java`
- Modify: `server/src/main/java/com/hvac/simulator/server/delivery/CentralHvacPointMapper.java`
- Modify: `server/src/main/java/com/hvac/simulator/server/api/dto/MqttDeliveryDtos.java`
- Modify: `server/src/main/java/com/hvac/simulator/server/delivery/MqttDeliveryService.java`
- Modify: `server/src/test/java/com/hvac/simulator/server/delivery/CentralHvacPointMapperTest.java`
- Modify: `server/src/test/java/com/hvac/simulator/server/delivery/MqttDeliveryServiceTest.java`

**Interfaces:**
- Produces: `CentralHvacMetricTarget.WCR_COP`、`TOWER_EFF`。
- Produces: `mapWcrCop(step, buildingId, chillerDeviceId, timestamp): List<CentralHvacPoint>`。
- Produces: `mapTowerEfficiency(step, buildingId, towerDeviceId, timestamp): List<CentralHvacPoint>`，停止时返回空列表。
- Extends: `CreateRequest(..., String deviceId, String coolingTowerDeviceId, Set<CentralHvacMetricTarget> targets)`。

- [ ] **Step 1: 写映射失败测试**

```java
List<CentralHvacPoint> tower = mapper.mapTowerEfficiency(
        runningStep, "BLD001", "TOWER1", timestamp);
assertEquals(List.of("TOWER1_TCWin", "TOWER1_TCWout", "TOWER1_TWB"),
        tower.stream().map(CentralHvacPoint::pointCode).toList());
assertEquals(runningStep.coolingWaterReturnSensorC(), tower.get(0).value());
assertEquals(runningStep.coolingWaterSupplySensorC(), tower.get(1).value());
assertEquals(runningStep.wetBulbC(), tower.get(2).value());
```

增加停止时间步断言返回空列表，并用 `(TWin-TWout)/(TWin-TWB)*100` 核对运行步效率。

- [ ] **Step 2: 运行映射失败测试**

Run: `./mvnw.cmd -pl server -Dtest=CentralHvacPointMapperTest test`

Expected: `mapTowerEfficiency` 不存在。

- [ ] **Step 3: 实现独立映射方法**

保留 `message` 和标识符校验；原 `map` 重命名为 `mapWcrCop`。冷却塔方法先判断：

```java
if (step.coolingTowerFanPowerKw() <= 0.0
        || step.coolingWaterFlowSensorM3PerSecond() <= 0.0) {
    return List.of();
}
```

然后按设计中的三个来源编码和方向构造测点。注释必须说明回水是塔进水、供水是塔出水，以及 TWB 来自气象输入。

- [ ] **Step 4: 写服务兼容与双目标失败测试**

覆盖：

```java
new CreateRequest(0, 2, REBASE_TO_NOW, "BLD001", "WCR1", null, null)
```

仍发布 12 条；显式 `Set.of(WCR_COP, TOWER_EFF)` 按所选范围实际运行步计算总消息数；仅 `TOWER_EFF` 的无运行区间抛出“所选范围没有冷却塔运行时间步”。

- [ ] **Step 5: 实现目标解析和预先生成消息**

`MqttDeliveryService.create` 将缺失目标规范为 `Set.of(WCR_COP)`，拒绝空集合。对每个时间步和目标调用映射方法，先形成不可变 `List<MqttPublishMessage>`；列表为空时拒绝。`MqttDelivery.totalMessages` 使用列表长度，异步发布只消费该快照，避免预估与实际不一致。

- [ ] **Step 6: 运行 server 定向测试**

Run: `./mvnw.cmd -pl server -Dtest=CentralHvacPointMapperTest,MqttDeliveryServiceTest,SimulationPlatformApiTest test`

Expected: 映射、兼容、消息计数和 API 回归全部通过。

- [ ] **Step 7: 提交冷却塔发送能力**

```powershell
git add -- server/src/main/java/com/hvac/simulator/server/delivery/CentralHvacMetricTarget.java `
  server/src/main/java/com/hvac/simulator/server/delivery/CentralHvacPointMapper.java `
  server/src/main/java/com/hvac/simulator/server/api/dto/MqttDeliveryDtos.java `
  server/src/main/java/com/hvac/simulator/server/delivery/MqttDeliveryService.java `
  server/src/test/java/com/hvac/simulator/server/delivery/CentralHvacPointMapperTest.java `
  server/src/test/java/com/hvac/simulator/server/delivery/MqttDeliveryServiceTest.java
git commit -m "feat(mqtt): 接入 Gaia 冷却塔效率测点"
```

### Task 5: Vue 配置双指标 MQTT 投递

**Files:**
- Modify: `web/src/features/simulation-task/model/types.ts`
- Modify: `web/src/features/simulation-task/ports/SimulationPlatformPort.ts`
- Modify: `web/src/features/simulation-task/application/useSimulationWorkbench.ts`
- Modify: `web/src/features/simulation-task/application/useSimulationWorkbench.test.ts`
- Modify: `web/src/features/simulation-task/components/MqttDeliveryPanel.vue`
- Create: `web/src/features/simulation-task/components/MqttDeliveryPanel.test.ts`
- Modify: `web/src/features/simulation-task/adapters/httpSimulationPlatformAdapter.test.ts`
- Modify: `web/tests/e2e/simulation-workbench.spec.ts`

**Interfaces:**
- Produces: `CentralHvacMetricTarget = 'WCR_COP'|'TOWER_EFF'`。
- Extends delivery input with `targets: CentralHvacMetricTarget[]` and `coolingTowerDeviceId: string`。

- [ ] **Step 1: 写组件失败测试**

挂载面板后断言两个复选框默认选中、冷机默认 `WCR1`、冷却塔默认 `TOWER1`；取消全部目标时按钮禁用并显示“至少选择一个指标”；提交事件精确包含：

```ts
{
  fromStep: 0,
  toStep: 59,
  timeMode: 'REBASE_TO_NOW',
  buildingId: 'BLD001',
  deviceId: 'WCR1',
  coolingTowerDeviceId: 'TOWER1',
  targets: ['WCR_COP', 'TOWER_EFF'],
}
```

- [ ] **Step 2: 运行失败测试**

Run: `pnpm run test:run -- src/features/simulation-task/components/MqttDeliveryPanel.test.ts`

Working directory: `web`

Expected: 测试文件或新控件不存在。

- [ ] **Step 3: 实现类型、端口和面板**

更新接口和 composable 透传新增字段。面板说明改为“可发送冷水机组 COP 与冷却塔效率所需真实测点”，两个指标复选框默认选中；只有选择 `TOWER_EFF` 时显示/启用冷却塔设备输入。

- [ ] **Step 4: 更新适配器、composable 和 E2E 测试**

适配器测试断言 POST JSON 包含 `targets` 与 `coolingTowerDeviceId`；composable 测试断言不改写选择；Playwright 断言两个目标默认选中和冷却塔设备框可见。

- [ ] **Step 5: 运行前端完整验证**

Run: `pnpm run check`

Run: `pnpm run test:run`

Run: `pnpm run build`

Working directory: `web`

Expected: 类型检查、Vitest 和生产构建全部通过。

- [ ] **Step 6: 提交前端投递配置**

```powershell
git add -- web/src/features/simulation-task/model/types.ts `
  web/src/features/simulation-task/ports/SimulationPlatformPort.ts `
  web/src/features/simulation-task/application/useSimulationWorkbench.ts `
  web/src/features/simulation-task/application/useSimulationWorkbench.test.ts `
  web/src/features/simulation-task/components/MqttDeliveryPanel.vue `
  web/src/features/simulation-task/components/MqttDeliveryPanel.test.ts `
  web/src/features/simulation-task/adapters/httpSimulationPlatformAdapter.test.ts `
  web/tests/e2e/simulation-workbench.spec.ts
git commit -m "feat(web): 配置冷却塔效率投递"
```

### Task 6: 自动化、联调脚本、文档与交付

**Files:**
- Create: `scripts/Verify-Gaia11CentralHvacTowerEfficiency.ps1`
- Modify: `web/tests/e2e/simulation-workbench.spec.ts`
- Modify: `PROJECT_GUIDE.md`
- Modify: `PROJECT_STATUS.md`

**Interfaces:**
- Consumes: 仿真任务 API、双目标 MQTT API、中央平台 latest/trends API。
- Produces: 成功标记 `CENTRAL_HVAC_TOWER_EFFICIENCY_VERIFIED` 和逐分钟差异摘要。

- [ ] **Step 1: 编写冷却塔联调脚本**

脚本复用 COP 脚本的 `Invoke-JsonRequest`、`Wait-Until`、Broker 和 JWT 预检，但从结果行选择同时满足 `ct_fan_power_kW > 0` 与 `cw_flow_sensor > 0` 的样本。每个样本按：

```powershell
$expected = ($row.T_cw_return_sensor - $row.T_cw_supply_sensor) /
    ($row.T_cw_return_sensor - $row.T_wb) * 100.0
```

构造预期值；POST 显式使用 `targets = @('TOWER_EFF')`、`coolingTowerDeviceId = 'TOWER1'`。轮询中央平台 `TOWER_EFF` 最新值和趋势，容差为 `max(1e-8, abs(expected)*1e-8)`。

- [ ] **Step 2: 运行仓库完整自动化**

Run: `./mvnw.cmd test`

Run: `./mvnw.cmd package`

Expected: 全部 Maven 测试与打包通过；Gaia 1.0/1.1 基准未变化。

Run: `pnpm run check`

Run: `pnpm run test:run`

Run: `pnpm run build`

Run: `pnpm run test:e2e`

Working directory: `web`

Expected: 类型、单元/组件、构建和 Playwright 全部通过。

- [ ] **Step 3: 启动本地完整软件栈并执行冷却塔验收**

使用现有本地 EMQX 与中央平台，不修改中央平台代码。以环境变量提供 JWT，执行：

```powershell
./scripts/Verify-Gaia11CentralHvacTowerEfficiency.ps1 `
  -SimulatorBaseUrl http://127.0.0.1:8080 `
  -PlatformBaseUrl http://127.0.0.1:8081/api
```

Expected: `result= CENTRAL_HVAC_TOWER_EFFICIENCY_VERIFIED`，MQTT 消息数、比较点数和最大绝对差明确输出。外部依赖不可用时记录未验证，不用模拟结果替代。

- [ ] **Step 4: 浏览器确认页面**

在仿真工作台确认两个版本参数分区、双指标发送配置；在中央空调页面确认 `TOWER_EFF` 最新值和趋势曲线来自本次回放。记录页面确认与数值自动化是不同证据。

- [ ] **Step 5: 更新项目指南和状态**

`PROJECT_GUIDE.md` 更新稳定参数归属合同、七测点可选链路和验收脚本入口。`PROJECT_STATUS.md` 修正 PR #11 已合并的历史状态，并按本次真实结果分别记录：已实现、自动化已测试、本地中央平台已验证或未验证、真实现场未验证；水泵和 AHU 继续列为未支持且不得伪造。

- [ ] **Step 6: 注释与差异自检**

完整检查变化生产文件中的版本归属、温度方向、TWB 来源、停止时间步、兼容默认和异步消息快照注释。简单 DTO 和显然的 Vue 绑定不补逐行注释。

Run: `git diff --check`

Run: `git status --short`

Expected: 无空白错误、敏感信息、`target`、`node_modules`、运行日志或临时输出进入差异。

- [ ] **Step 7: 提交文档与验收资产**

```powershell
git add -- scripts/Verify-Gaia11CentralHvacTowerEfficiency.ps1 `
  PROJECT_GUIDE.md PROJECT_STATUS.md web/tests/e2e/simulation-workbench.spec.ts
git commit -m "test(integration): 验证冷却塔效率完整链路"
```

- [ ] **Step 8: 推送并创建 PR**

```powershell
git push -u origin feature/gaia-versioned-parameters-tower-efficiency
gh pr create --base main --head feature/gaia-versioned-parameters-tower-efficiency `
  --title "feat(platform): 隔离版本参数并接入冷却塔效率" `
  --body-file <生成的临时 PR 正文文件>
```

PR 正文列出范围、非目标、所有实际测试、本地联调结果、未验证项和兼容行为。未合并前不描述为 `main` 能力。

---

## Plan Self-Review

- **设计覆盖：** Task 1~3 覆盖独立参数目录、公共/专属标注、切换隔离；Task 4~6 覆盖冷却塔映射、可选发送、兼容、前端、完整联调与文档。
- **模型边界：** 没有任务修改 Python 基准、Java 公式、30 字段、随机顺序或增加缺失水泵/AHU 测点。
- **接口一致：** `ParameterScope` 在引擎、DTO 和 TypeScript 中同名；MQTT `targets`、`coolingTowerDeviceId` 在 DTO、端口、composable、组件和脚本中一致。
- **兼容检查：** 缺失 `targets` 明确保持四点 WCR 行为；页面显式选择双目标；消息总数来自实际映射快照。
- **证据边界：** 单元测试、基准一致性、本地 MQTT/平台趋势、浏览器页面和真实现场分别报告。
- **占位符检查：** 计划不含未决实现项；外部环境不可用时唯一允许结论是“本地完整联调未验证”。

执行方式已由用户确认：采用当前会话内联执行，计划自检后连续实施，不启用子代理。
