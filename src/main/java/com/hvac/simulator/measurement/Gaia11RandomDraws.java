package com.hvac.simulator.measurement;

/** 单时间步的标准化随机抽样；电表停机时不消费均匀随机数。 */
public record Gaia11RandomDraws(
        Double powerUniformUnit,
        double chilledWaterFlowNormal,
        double coolingWaterFlowNormal,
        double chilledWaterSupplyTemperatureNormal,
        double chilledWaterReturnTemperatureNormal,
        double coolingWaterSupplyTemperatureNormal,
        double coolingWaterReturnTemperatureNormal) {

    public Gaia11RandomDraws {
        requireFinite(chilledWaterFlowNormal);
        requireFinite(coolingWaterFlowNormal);
        requireFinite(chilledWaterSupplyTemperatureNormal);
        requireFinite(chilledWaterReturnTemperatureNormal);
        requireFinite(coolingWaterSupplyTemperatureNormal);
        requireFinite(coolingWaterReturnTemperatureNormal);
        if (powerUniformUnit != null && (!Double.isFinite(powerUniformUnit)
                || powerUniformUnit < -1.0 || powerUniformUnit > 1.0)) {
            throw new IllegalArgumentException("电表标准化随机数必须位于 [-1, 1]");
        }
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("标准化高斯随机数必须是有限数值");
        }
    }
}
