package com.hvac.simulator.model;

import com.hvac.simulator.config.BuildingEnvelope;
import com.hvac.simulator.config.InternalLoad;
import java.util.Objects;

/**
 * 一阶集总热容建筑模型，负责把内部得热、太阳得热、围护传热、渗透和供冷合成为室温变化。
 * 热量进入室内为正，空调供冷为负，所有功率在本类中统一使用 W。
 */
public final class BuildingThermalModel {

    private static final double WALL_AREA_M2 = 3_000.0;
    private static final double WINDOW_AREA_M2 = 1_500.0;
    private static final double ROOF_AREA_M2 = 1_200.0;
    private static final double AIR_DENSITY_KG_PER_M3 = 1.2;
    private static final double AIR_SPECIFIC_HEAT_J_PER_KG_K = 1_005.0;
    private static final double LATENT_HEAT_VAPORIZATION_J_PER_KG = 2_450_000.0;

    private final BuildingEnvelope envelope;
    private final InternalLoad internalLoad;
    private final double totalUaWPerK;
    private final double conditionedVolumeM3;

    public BuildingThermalModel(BuildingEnvelope envelope, InternalLoad internalLoad) {
        this.envelope = Objects.requireNonNull(envelope, "围护结构参数不能为空");
        this.internalLoad = Objects.requireNonNull(internalLoad, "内部负荷参数不能为空");
        totalUaWPerK = envelope.wallUValueWPerM2K() * WALL_AREA_M2
                + envelope.roofUValueWPerM2K() * ROOF_AREA_M2
                + envelope.windowUValueWPerM2K() * WINDOW_AREA_M2;
        conditionedVolumeM3 = envelope.conditionedAreaM2()
                * envelope.floorHeightM() * envelope.floorCount();
    }

    /** 根据 Gaia 固定办公时间表返回内部显热和潜热，单位 W。 */
    public InternalGains internalGains(double hourOfDay, boolean weekday) {
        double schedule = scheduleFactor(hourOfDay, weekday);
        double occupants = envelope.conditionedAreaM2() * internalLoad.occupancyPerM2();
        double sensibleOccupants = occupants * internalLoad.sensibleWPerPerson() * schedule;
        double latentOccupants = occupants * internalLoad.latentWPerPerson() * schedule;
        double lighting = envelope.conditionedAreaM2() * internalLoad.lightingWPerM2() * schedule;
        double equipment = envelope.conditionedAreaM2() * internalLoad.equipmentWPerM2() * schedule;
        return new InternalGains(sensibleOccupants + lighting + equipment, latentOccupants);
    }

    /**
     * 计算预留的新风显热和潜热负荷，正值表示室外空气向室内增热。
     * 含湿量参数沿用 Gaia 的 g/kg 假设；该方法暂不接入主仿真链。
     */
    public InternalGains outdoorAirLoad(
            double indoorC,
            double outdoorC,
            double indoorHumidityGPerKg,
            double outdoorHumidityGPerKg,
            boolean weekday,
            double hourOfDay) {
        double occupants = envelope.conditionedAreaM2() * internalLoad.occupancyPerM2()
                * scheduleFactor(hourOfDay, weekday);
        double flowM3PerSecond = occupants * internalLoad.outdoorAirM3PerHourPerson() / 3_600.0;
        double sensible = AIR_DENSITY_KG_PER_M3 * AIR_SPECIFIC_HEAT_J_PER_KG_K
                * flowM3PerSecond * (outdoorC - indoorC);
        double latent = AIR_DENSITY_KG_PER_M3 * LATENT_HEAT_VAPORIZATION_J_PER_KG
                * flowM3PerSecond * (outdoorHumidityGPerKg - indoorHumidityGPerKg) * 1e-3;
        return new InternalGains(sensible, latent);
    }

    /** 汇总不含空调的室内净显热，单位 W，进入室内为正。 */
    public double netSensibleGainWithoutHvac(
            double indoorC,
            double outdoorC,
            double solarGlobalWPerM2,
            double hourOfDay,
            boolean weekday) {
        double internal = internalGains(hourOfDay, weekday).sensibleW();
        double solar = WINDOW_AREA_M2 * envelope.solarHeatGainCoefficient() * solarGlobalWPerM2;
        double envelopeHeat = (outdoorC - indoorC) * totalUaWPerK;
        double infiltrationFlowM3PerSecond = envelope.infiltrationAirChangesPerHour()
                * conditionedVolumeM3 / 3_600.0;
        double infiltration = AIR_DENSITY_KG_PER_M3 * AIR_SPECIFIC_HEAT_J_PER_KG_K
                * infiltrationFlowM3PerSecond * (outdoorC - indoorC);
        return internal + solar + envelopeHeat + infiltration;
    }

    /**
     * 按显式欧拉法更新室温：Tnew = T + (Qnet * dt) / C。
     * dt 单位为秒，建筑热容单位 J/K；不做数值稳定化，以保持 Gaia 1.0 结果。
     */
    public double step(
            double indoorC,
            double outdoorC,
            double solarGlobalWPerM2,
            double hourOfDay,
            boolean weekday,
            double sensibleSupplyW,
            double dtSeconds) {
        double netHeatW = netSensibleGainWithoutHvac(
                indoorC, outdoorC, solarGlobalWPerM2, hourOfDay, weekday) + sensibleSupplyW;
        return indoorC + netHeatW * dtSeconds / envelope.thermalCapacityJPerK();
    }

    public double thermalCapacityJPerK() {
        return envelope.thermalCapacityJPerK();
    }

    public double totalUaWPerK() {
        return totalUaWPerK;
    }

    private double scheduleFactor(double hourOfDay, boolean weekday) {
        int hour = Math.floorMod((int) hourOfDay, 24);
        if (weekday) {
            return hour >= 8 && hour < 18 ? 1.0 : 0.0;
        }
        return hour >= 10 && hour < 16 ? 0.5 : 0.0;
    }
}
