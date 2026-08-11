package com.hvac.simulator.server.application;

import com.hvac.simulator.config.SimulationConfig;
import com.hvac.simulator.config.WeatherParameters;
import com.hvac.simulator.measurement.FrozenRandomDrawSource;
import com.hvac.simulator.measurement.Gaia11MeasurementModel;
import com.hvac.simulator.measurement.SeededRandomDrawSource;
import com.hvac.simulator.model.BuildingThermalModel;
import com.hvac.simulator.model.HvacSystem;
import com.hvac.simulator.release.ModelReleaseCatalog;
import com.hvac.simulator.release.ModelVersion;
import com.hvac.simulator.simulation.Gaia11SimulationStep;
import com.hvac.simulator.simulation.Gaia11Simulator;
import com.hvac.simulator.simulation.SimulationStep;
import com.hvac.simulator.simulation.Simulator;
import com.hvac.simulator.weather.BaselineWeatherSource;
import com.hvac.simulator.weather.Gaia11BaselineWeatherSource;
import com.hvac.simulator.weather.SyntheticWeatherGenerator;
import com.hvac.simulator.weather.WeatherSource;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.CreateRequest;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.Created;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.Rows;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.RunView;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.Series;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.SeriesGroup;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.SeriesResponse;
import com.hvac.simulator.server.domain.SimulationMode;
import com.hvac.simulator.server.domain.SimulationRun;
import com.hvac.simulator.server.domain.SimulationRunOutput;
import com.hvac.simulator.server.domain.SimulationRunRepository;
import com.hvac.simulator.server.domain.SimulationRunStatus;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SimulationRunService {
    private static final Logger LOG = LoggerFactory.getLogger(SimulationRunService.class);
    private static final String GAIA_10_BASELINE = "gaia-baseline/python-results.csv";
    private static final String GAIA_11_WEATHER = "gaia-baseline/gaia-1.1/python-weather.csv";
    private static final String GAIA_11_RANDOM = "gaia-baseline/gaia-1.1/python-random-draws.csv";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ModelReleaseCatalog catalog;
    private final SimulationRunRepository repository;
    private final ExecutorService executor;

    public SimulationRunService(
            ModelReleaseCatalog catalog, SimulationRunRepository repository, ExecutorService executor) {
        this.catalog = catalog;
        this.repository = repository;
        this.executor = executor;
    }

    public Created create(CreateRequest request) {
        if (request == null || request.modelVersion() == null || request.mode() == null) {
            throw new IllegalArgumentException("模型版本和运行模式不能为空");
        }
        Map<String, Double> overrides = request.overrides() == null ? Map.of() : request.overrides();
        if (request.mode() == SimulationMode.BASELINE && !overrides.isEmpty()) {
            throw new IllegalArgumentException("基准模式不允许修改参数");
        }
        ModelVersion version = ModelVersion.parse(request.modelVersion());
        long seed = request.seed() == null ? 20240810L : request.seed();
        var snapshot = catalog.applyOverrides(version, overrides);
        var run = new SimulationRun(UUID.randomUUID(), request.mode(), seed, snapshot);
        repository.save(run);
        executor.execute(() -> execute(run));
        return new Created(run.id(), SimulationRunStatus.QUEUED);
    }

    public RunView view(UUID id) {
        SimulationRun run = find(id);
        return new RunView(
                run.id(), run.parameters().version().code(), run.mode(), run.seed(),
                run.parameters().overrides(), run.status(), run.completedSteps(), run.totalSteps(),
                run.simulationTime(), run.errorCode(), run.errorMessage(), run.createdAt());
    }

    public SeriesResponse series(UUID id) {
        SimulationRun run = completed(id);
        return run.parameters().version() == ModelVersion.GAIA_1_1
                ? gaia11Series(run.output().gaia11()) : gaia10Series(run.output().gaia10());
    }

    public Rows rows(UUID id, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("offset 必须非负且 limit 必须在 1~1000");
        }
        SimulationRun run = completed(id);
        int total = run.parameters().version() == ModelVersion.GAIA_1_1
                ? run.output().gaia11().steps().size() : run.output().gaia10().steps().size();
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);
        List<Map<String, Object>> items = new ArrayList<>(to - from);
        if (run.parameters().version() == ModelVersion.GAIA_1_1) {
            for (var step : run.output().gaia11().steps().subList(from, to)) {
                items.add(gaia11Row(step));
            }
        } else {
            for (var step : run.output().gaia10().steps().subList(from, to)) {
                items.add(gaia10Row(step));
            }
        }
        return new Rows(offset, limit, total, items);
    }

    public SimulationRun completedRun(UUID id) {
        return completed(id);
    }

    private void execute(SimulationRun run) {
        try {
            SimulationConfig config = SimulationConfig.gaiaDemo(run.seed());
            run.start(config.expectedSteps());
            var snapshot = run.parameters();
            var building = new BuildingThermalModel(snapshot.building(), snapshot.internalLoad());
            var hvac = new HvacSystem(snapshot.hvac());
            WeatherSource weather = weatherSource(run);
            if (snapshot.version() == ModelVersion.GAIA_1_1) {
                var random = run.mode() == SimulationMode.BASELINE
                        ? new FrozenRandomDrawSource(GAIA_11_RANDOM)
                        : new SeededRandomDrawSource(run.seed());
                var result = new Gaia11Simulator(
                        config, building, hvac, new Gaia11MeasurementModel(snapshot.measurement()), random)
                        .run(weather.load(config), run::progress);
                run.complete(SimulationRunOutput.gaia11(result));
            } else {
                var result = new Simulator(config, building, hvac)
                        .run(weather.load(config), run::progress);
                run.complete(SimulationRunOutput.gaia10(result));
            }
        } catch (Exception exception) {
            LOG.error("仿真任务失败，runId={}", run.id(), exception);
            run.fail("SIMULATION_FAILED", "仿真执行失败");
        }
    }

    private WeatherSource weatherSource(SimulationRun run) {
        if (run.mode() == SimulationMode.SCENARIO) {
            return new SyntheticWeatherGenerator(WeatherParameters.gaiaDefaults());
        }
        return run.parameters().version() == ModelVersion.GAIA_1_1
                ? new Gaia11BaselineWeatherSource(GAIA_11_WEATHER)
                : new BaselineWeatherSource(GAIA_10_BASELINE);
    }

    private SimulationRun completed(UUID id) {
        SimulationRun run = find(id);
        if (run.status() != SimulationRunStatus.COMPLETED) {
            throw new IllegalStateException("任务尚未完成：" + run.status());
        }
        return run;
    }

    private SimulationRun find(UUID id) {
        return repository.find(id).orElseThrow(() -> new NoSuchElementException("仿真任务不存在"));
    }

    private SeriesResponse gaia11Series(com.hvac.simulator.simulation.Gaia11SimulationResult result) {
        List<String> timestamps = result.steps().stream().map(s -> s.timestamp().format(TIME)).toList();
        return new SeriesResponse(timestamps, List.of(
                group("temperature", "室内外温度", "℃", List.of(
                        series("T_room", "室温", result, Gaia11SimulationStep::roomC),
                        series("T_outdoor", "室外温度", result, Gaia11SimulationStep::outdoorC))),
                group("load", "冷负荷", "kW", List.of(
                        series("cooling_load_kW", "冷负荷", result, Gaia11SimulationStep::coolingLoadKw))),
                group("power", "系统与冷机功率", "kW", List.of(
                        series("total_power_kW", "系统总功率（测量）", result, Gaia11SimulationStep::totalPowerKw),
                        series("chiller_power_true_kW", "冷机功率（理论）", result, Gaia11SimulationStep::chillerPowerTrueKw),
                        series("chiller_power_kW", "冷机功率（测量）", result, Gaia11SimulationStep::chillerPowerKw))),
                group("cop", "理论与测量 COP", "-", List.of(
                        series("chiller_COP", "COP（理论）", result, Gaia11SimulationStep::chillerCop),
                        series("measured_COP", "COP（测量）", result, Gaia11SimulationStep::measuredCop))),
                group("water-temperature", "测量水温", "℃", List.of(
                        series("T_chw_supply_sensor", "冷冻水供水", result, Gaia11SimulationStep::chilledWaterSupplySensorC),
                        series("T_chw_return_sensor", "冷冻水回水", result, Gaia11SimulationStep::chilledWaterReturnSensorC),
                        series("T_cw_supply_sensor", "冷却水供水", result, Gaia11SimulationStep::coolingWaterSupplySensorC),
                        series("T_cw_return_sensor", "冷却水回水", result, Gaia11SimulationStep::coolingWaterReturnSensorC)))));
    }

    private SeriesResponse gaia10Series(com.hvac.simulator.simulation.SimulationResult result) {
        List<String> timestamps = result.steps().stream().map(s -> s.timestamp().format(TIME)).toList();
        return new SeriesResponse(timestamps, List.of(
                group("temperature", "室内外温度", "℃", List.of(
                        series10("T_room", "室温", result, SimulationStep::roomC),
                        series10("T_outdoor", "室外温度", result, SimulationStep::outdoorC))),
                group("load", "冷负荷", "kW", List.of(
                        series10("cooling_load_kW", "冷负荷", result, SimulationStep::coolingLoadKw))),
                group("power", "系统与冷机功率", "kW", List.of(
                        series10("total_power_kW", "系统总功率", result, SimulationStep::totalPowerKw),
                        series10("chiller_power_kW", "冷机功率", result, SimulationStep::chillerPowerKw))),
                group("cop", "理论 COP", "-", List.of(
                        series10("chiller_COP", "COP", result, SimulationStep::chillerCop))),
                group("water-temperature", "水温", "℃", List.of(
                        series10("T_chw_supply", "冷冻水供水", result, SimulationStep::chilledWaterSupplyC),
                        series10("T_cw_supply", "冷却水供水", result, SimulationStep::coolingWaterSupplyC)))));
    }

    private SeriesGroup group(String code, String title, String unit, List<Series> series) {
        return new SeriesGroup(code, title, unit, series);
    }

    private Series series(
            String code, String label, com.hvac.simulator.simulation.Gaia11SimulationResult result,
            ToDoubleFunction<Gaia11SimulationStep> extractor) {
        return new Series(code, label, result.steps().stream().map(extractor::applyAsDouble).toList());
    }

    private Series series10(
            String code, String label, com.hvac.simulator.simulation.SimulationResult result,
            ToDoubleFunction<SimulationStep> extractor) {
        return new Series(code, label, result.steps().stream().map(extractor::applyAsDouble).toList());
    }

    private Map<String, Object> gaia11Row(Gaia11SimulationStep s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("datetime", s.timestamp().format(TIME));
        row.put("T_outdoor", s.outdoorC());
        row.put("T_wb", s.wetBulbC());
        row.put("solar", s.solarGlobalWPerM2());
        row.put("T_room", s.roomC());
        row.put("cooling_load_kW", s.coolingLoadKw());
        row.put("chiller_power_kW", s.chillerPowerKw());
        row.put("chw_pump_power_kW", s.chilledWaterPumpPowerKw());
        row.put("cw_pump_power_kW", s.coolingWaterPumpPowerKw());
        row.put("ct_fan_power_kW", s.coolingTowerFanPowerKw());
        row.put("terminal_fan_power_kW", s.terminalFanPowerKw());
        row.put("total_power_kW", s.totalPowerKw());
        row.put("chiller_PLR", s.chillerPlr());
        row.put("chiller_COP", s.chillerCop());
        row.put("chw_flow_rate", s.chilledWaterFlowM3PerSecond());
        row.put("cw_flow_rate", s.coolingWaterFlowM3PerSecond());
        row.put("T_chw_supply", s.chilledWaterSupplyC());
        row.put("T_chw_return", s.chilledWaterReturnC());
        row.put("T_cw_supply", s.coolingWaterSupplyC());
        row.put("T_cw_return", s.coolingWaterReturnC());
        row.put("pipe_heat_gain_kW", s.pipeHeatGainKw());
        row.put("chw_flow_sensor", s.chilledWaterFlowSensorM3PerSecond());
        row.put("cw_flow_sensor", s.coolingWaterFlowSensorM3PerSecond());
        row.put("T_chw_supply_sensor", s.chilledWaterSupplySensorC());
        row.put("T_chw_return_sensor", s.chilledWaterReturnSensorC());
        row.put("T_cw_supply_sensor", s.coolingWaterSupplySensorC());
        row.put("T_cw_return_sensor", s.coolingWaterReturnSensorC());
        row.put("chiller_power_true_kW", s.chillerPowerTrueKw());
        row.put("measured_cooling_kW", s.measuredCoolingKw());
        row.put("measured_COP", s.measuredCop());
        return row;
    }

    private Map<String, Object> gaia10Row(SimulationStep s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("datetime", s.timestamp().format(TIME));
        row.put("T_outdoor", s.outdoorC());
        row.put("T_wb", s.wetBulbC());
        row.put("solar", s.solarGlobalWPerM2());
        row.put("T_room", s.roomC());
        row.put("cooling_load_kW", s.coolingLoadKw());
        row.put("chiller_power_kW", s.chillerPowerKw());
        row.put("chw_pump_power_kW", s.chilledWaterPumpPowerKw());
        row.put("cw_pump_power_kW", s.coolingWaterPumpPowerKw());
        row.put("ct_fan_power_kW", s.coolingTowerFanPowerKw());
        row.put("terminal_fan_power_kW", s.terminalFanPowerKw());
        row.put("total_power_kW", s.totalPowerKw());
        row.put("chiller_PLR", s.chillerPlr());
        row.put("chiller_COP", s.chillerCop());
        row.put("T_chw_supply", s.chilledWaterSupplyC());
        row.put("T_cw_supply", s.coolingWaterSupplyC());
        row.put("pipe_heat_gain_kW", s.pipeHeatGainKw());
        return row;
    }
}
