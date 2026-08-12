package com.hvac.simulator.server.delivery;

import com.hvac.simulator.simulation.Gaia11SimulationStep;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 把 Gaia 1.1 已有测量值映射为中央平台 WCR_COP 与 TOWER_EFF 的真实输入。 */
@Component
public class CentralHvacPointMapper {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public List<CentralHvacPoint> mapWcrCop(
            Gaia11SimulationStep step, String buildingId, String deviceId, long timestamp) {
        Objects.requireNonNull(step, "Gaia 1.1 时间步不能为空");
        validateIdentifier(buildingId, "buildingId");
        validateIdentifier(deviceId, "deviceId");
        return List.of(
                new CentralHvacPoint(buildingId, deviceId, deviceId + "_TWin",
                        step.chilledWaterReturnSensorC(), timestamp),
                new CentralHvacPoint(buildingId, deviceId, deviceId + "_TWout",
                        step.chilledWaterSupplySensorC(), timestamp),
                new CentralHvacPoint(buildingId, deviceId, deviceId + "_Flow",
                        step.chilledWaterFlowSensorM3PerSecond() * 3_600.0, timestamp),
                new CentralHvacPoint(buildingId, deviceId, deviceId + "_PPE",
                        step.chillerPowerKw(), timestamp));
    }

    public List<CentralHvacPoint> mapTowerEfficiency(
            Gaia11SimulationStep step, String buildingId, String deviceId, long timestamp) {
        Objects.requireNonNull(step, "Gaia 1.1 时间步不能为空");
        validateIdentifier(buildingId, "buildingId");
        validateIdentifier(deviceId, "coolingTowerDeviceId");
        if (step.coolingTowerFanPowerKw() <= 0.0
                || step.coolingWaterFlowSensorM3PerSecond() <= 0.0) {
            return List.of();
        }
        // Gaia 的冷却水回水进入冷却塔，供水从冷却塔流出；TWB 来自同一步气象输入。
        return List.of(
                new CentralHvacPoint(buildingId, deviceId, deviceId + "_TCWin",
                        step.coolingWaterReturnSensorC(), timestamp),
                new CentralHvacPoint(buildingId, deviceId, deviceId + "_TCWout",
                        step.coolingWaterSupplySensorC(), timestamp),
                new CentralHvacPoint(buildingId, deviceId, deviceId + "_TWB",
                        step.wetBulbC(), timestamp));
    }

    public double centralPlatformCop(List<CentralHvacPoint> points) {
        double inlet = value(points, "_TWin");
        double outlet = value(points, "_TWout");
        double flowM3PerHour = value(points, "_Flow");
        double powerKw = value(points, "_PPE");
        double coolingKw = flowM3PerHour * 1_000.0 * 4.18 * (inlet - outlet) / 3_600.0;
        return flowM3PerHour > 0.0 && inlet > outlet && powerKw > 0.0
                ? coolingKw / powerKw : 0.0;
    }

    public MqttPublishMessage message(String topic, CentralHvacPoint point) {
        String payload = "{\"buildingId\":\"" + point.buildingId()
                + "\",\"deviceId\":\"" + point.deviceId()
                + "\",\"pointCode\":\"" + point.pointCode()
                + "\",\"val\":" + Double.toString(point.value())
                + ",\"timestamp\":" + point.timestamp() + "}";
        return new MqttPublishMessage(topic, 1, false, payload);
    }

    private double value(List<CentralHvacPoint> points, String suffix) {
        return points.stream().filter(point -> point.pointCode().endsWith(suffix))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("缺少 WCR_COP 测点：" + suffix))
                .value();
    }

    private void validateIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 只能包含字母、数字、下划线和连字符");
        }
    }
}
