package com.hvac.simulator.release;

import com.hvac.simulator.config.BuildingEnvelope;
import com.hvac.simulator.config.Gaia11MeasurementParameters;
import com.hvac.simulator.config.HvacParameters;
import com.hvac.simulator.config.InternalLoad;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 提供 Gaia 1.0/1.1 的真实默认值，并把允许的覆盖构造成实际计算配置。 */
public final class ModelReleaseCatalog {
    private final Map<ModelVersion, ModelReleaseDescriptor> releases;

    public ModelReleaseCatalog() {
        List<ModelParameterDescriptor> physical = physicalParameters();
        releases = Map.of(
                ModelVersion.GAIA_1_0,
                new ModelReleaseDescriptor(ModelVersion.GAIA_1_0, "Gaia 1.0", 17, physical),
                ModelVersion.GAIA_1_1,
                new ModelReleaseDescriptor(ModelVersion.GAIA_1_1, "Gaia 1.1", 30,
                        concat(physical, measurementParameters())));
    }

    public List<ModelReleaseDescriptor> releases() {
        return List.of(releases.get(ModelVersion.GAIA_1_0), releases.get(ModelVersion.GAIA_1_1));
    }

    public ModelReleaseDescriptor release(ModelVersion version) {
        ModelReleaseDescriptor release = releases.get(version);
        if (release == null) {
            throw new IllegalArgumentException("未知模型版本：" + version);
        }
        return release;
    }

    public ModelParameterSnapshot applyOverrides(ModelVersion version, Map<String, Double> overrides) {
        Map<String, Double> requested = overrides == null ? Map.of() : Map.copyOf(overrides);
        Map<String, ModelParameterDescriptor> descriptors = new LinkedHashMap<>();
        release(version).parameters().forEach(parameter -> descriptors.put(parameter.code(), parameter));
        requested.forEach((code, value) -> {
            ModelParameterDescriptor descriptor = descriptors.get(code);
            if (descriptor == null) {
                throw new IllegalArgumentException("模型版本不支持参数：" + code);
            }
            descriptor.validate(value);
        });

        BuildingEnvelope b = BuildingEnvelope.gaiaDefaults();
        BuildingEnvelope building = new BuildingEnvelope(
                b.totalAreaM2(), value(requested, "building.conditionedAreaM2", b.conditionedAreaM2()),
                (int) value(requested, "building.floorCount", b.floorCount()),
                value(requested, "building.floorHeightM", b.floorHeightM()), b.volumePerFloorM3(),
                b.southWindowWallRatio(), b.northWindowWallRatio(), b.eastWindowWallRatio(),
                b.westWindowWallRatio(), value(requested, "building.wallUValueWPerM2K", b.wallUValueWPerM2K()),
                value(requested, "building.roofUValueWPerM2K", b.roofUValueWPerM2K()),
                value(requested, "building.windowUValueWPerM2K", b.windowUValueWPerM2K()),
                value(requested, "building.solarHeatGainCoefficient", b.solarHeatGainCoefficient()),
                value(requested, "building.thermalCapacityJPerK", b.thermalCapacityJPerK()),
                b.equivalentResistanceKPerW(),
                value(requested, "building.infiltrationAch", b.infiltrationAirChangesPerHour()));

        InternalLoad i = InternalLoad.gaiaDefaults();
        InternalLoad internal = new InternalLoad(
                value(requested, "internal.occupancyPerM2", i.occupancyPerM2()),
                value(requested, "internal.sensibleHeatWPerPerson", i.sensibleWPerPerson()),
                i.latentWPerPerson(), value(requested, "internal.lightingWPerM2", i.lightingWPerM2()),
                value(requested, "internal.equipmentWPerM2", i.equipmentWPerM2()),
                i.outdoorAirM3PerHourPerson());

        HvacParameters h = HvacParameters.gaiaDefaults();
        HvacParameters hvac = new HvacParameters(
                value(requested, "hvac.chillerCapacityKw", h.chillerRatedCapacityKw()),
                value(requested, "hvac.chillerRatedCop", h.chillerRatedCop()),
                value(requested, "hvac.chilledWaterSupplyTempC", h.chilledWaterSupplyC()),
                value(requested, "hvac.chilledWaterReturnTempC", h.chilledWaterReturnC()),
                h.coolingWaterSupplyC(), h.coolingWaterReturnC(), h.plrCurveA(), h.plrCurveB(),
                h.plrCurveC(), h.chilledWaterPipe(), h.coolingWaterPipe(), h.chilledWaterPump(),
                h.coolingWaterPump(), h.coolingTower(), h.terminal(),
                value(requested, "hvac.coolingSetpointC", h.coolingSetpointC()),
                h.heatingSetpointC(), value(requested, "hvac.deadbandC", h.deadbandC()));

        Gaia11MeasurementParameters measurement = null;
        if (version == ModelVersion.GAIA_1_1) {
            Gaia11MeasurementParameters m = Gaia11MeasurementParameters.gaiaDefaults();
            measurement = new Gaia11MeasurementParameters(
                    value(requested, "measurement.flowNoiseStdPercent", m.flowNoiseStdPercent()),
                    value(requested, "measurement.temperatureNoiseStdC", m.temperatureNoiseStdC()),
                    value(requested, "measurement.sensorBias", m.sensorBias()),
                    value(requested, "measurement.powerMeterAccuracyPercent", m.powerMeterAccuracyPercent()));
        }
        return new ModelParameterSnapshot(version, building, internal, hvac, measurement, requested);
    }

    private List<ModelParameterDescriptor> physicalParameters() {
        BuildingEnvelope b = BuildingEnvelope.gaiaDefaults();
        InternalLoad i = InternalLoad.gaiaDefaults();
        HvacParameters h = HvacParameters.gaiaDefaults();
        return List.of(
                number("building.conditionedAreaM2", "空调面积", "建筑", "m²", b.conditionedAreaM2(), 1, 1e7),
                integer("building.floorCount", "楼层数", "建筑", "层", b.floorCount(), 1, 200),
                number("building.floorHeightM", "层高", "建筑", "m", b.floorHeightM(), 1, 20),
                number("building.wallUValueWPerM2K", "外墙传热系数", "建筑", "W/(m²·K)", b.wallUValueWPerM2K(), 0, 10),
                number("building.roofUValueWPerM2K", "屋面传热系数", "建筑", "W/(m²·K)", b.roofUValueWPerM2K(), 0, 10),
                number("building.windowUValueWPerM2K", "窗传热系数", "建筑", "W/(m²·K)", b.windowUValueWPerM2K(), 0, 20),
                number("building.solarHeatGainCoefficient", "太阳得热系数", "建筑", "-", b.solarHeatGainCoefficient(), 0, 1),
                number("building.thermalCapacityJPerK", "建筑热容", "建筑", "J/K", b.thermalCapacityJPerK(), 1, 1e12),
                number("building.infiltrationAch", "渗透换气次数", "建筑", "1/h", b.infiltrationAirChangesPerHour(), 0, 20),
                number("internal.occupancyPerM2", "人员密度", "内部负荷", "人/m²", i.occupancyPerM2(), 0, 10),
                number("internal.sensibleHeatWPerPerson", "人员显热", "内部负荷", "W/人", i.sensibleWPerPerson(), 0, 1000),
                number("internal.lightingWPerM2", "照明密度", "内部负荷", "W/m²", i.lightingWPerM2(), 0, 1000),
                number("internal.equipmentWPerM2", "设备密度", "内部负荷", "W/m²", i.equipmentWPerM2(), 0, 1000),
                number("hvac.chillerCapacityKw", "冷机额定制冷量", "空调系统", "kW", h.chillerRatedCapacityKw(), 1, 1e7),
                number("hvac.chillerRatedCop", "冷机额定 COP", "空调系统", "-", h.chillerRatedCop(), 0.1, 30),
                number("hvac.chilledWaterSupplyTempC", "冷冻水供水温度", "空调系统", "℃", h.chilledWaterSupplyC(), -20, 30),
                number("hvac.chilledWaterReturnTempC", "冷冻水回水温度", "空调系统", "℃", h.chilledWaterReturnC(), -20, 50),
                number("hvac.coolingSetpointC", "制冷设定温度", "控制", "℃", h.coolingSetpointC(), 0, 50),
                number("hvac.deadbandC", "温控死区", "控制", "℃", h.deadbandC(), 0, 20));
    }

    private List<ModelParameterDescriptor> measurementParameters() {
        Gaia11MeasurementParameters m = Gaia11MeasurementParameters.gaiaDefaults();
        return List.of(
                number("measurement.flowNoiseStdPercent", "流量噪声标准差", "测量", "%", m.flowNoiseStdPercent(), 0, 100),
                number("measurement.temperatureNoiseStdC", "温度噪声标准差", "测量", "℃", m.temperatureNoiseStdC(), 0, 20),
                number("measurement.sensorBias", "传感器统一偏差", "测量", "原量纲", m.sensorBias(), -100, 100),
                number("measurement.powerMeterAccuracyPercent", "电能表精度", "测量", "%", m.powerMeterAccuracyPercent(), 0, 100));
    }

    private ModelParameterDescriptor number(
            String code, String label, String group, String unit,
            double defaultValue, double minimum, double maximum) {
        return new ModelParameterDescriptor(
                code, label, group, unit, ParameterValueType.NUMBER,
                defaultValue, minimum, maximum, true, null);
    }

    private ModelParameterDescriptor integer(
            String code, String label, String group, String unit,
            double defaultValue, double minimum, double maximum) {
        return new ModelParameterDescriptor(
                code, label, group, unit, ParameterValueType.INTEGER,
                defaultValue, minimum, maximum, true, null);
    }

    private double value(Map<String, Double> overrides, String code, double defaultValue) {
        return overrides.getOrDefault(code, defaultValue);
    }

    private List<ModelParameterDescriptor> concat(
            List<ModelParameterDescriptor> first, List<ModelParameterDescriptor> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
