package com.hvac.simulator.config;

/** 建筑几何、围护结构和热容参数，单位与 Gaia 1.0 保持一致。 */
public record BuildingEnvelope(
        double totalAreaM2,
        double conditionedAreaM2,
        int floorCount,
        double floorHeightM,
        double volumePerFloorM3,
        double southWindowWallRatio,
        double northWindowWallRatio,
        double eastWindowWallRatio,
        double westWindowWallRatio,
        double wallUValueWPerM2K,
        double roofUValueWPerM2K,
        double windowUValueWPerM2K,
        double solarHeatGainCoefficient,
        double thermalCapacityJPerK,
        double equivalentResistanceKPerW,
        double infiltrationAirChangesPerHour) {

    public static BuildingEnvelope gaiaDefaults() {
        return new BuildingEnvelope(
                12_000.0, 10_000.0, 10, 3.6, 4_320.0,
                0.5, 0.3, 0.4, 0.4,
                0.45, 0.35, 2.0, 0.35,
                5_000_000.0, 0.02, 0.2);
    }
}
