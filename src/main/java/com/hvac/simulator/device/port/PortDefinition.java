package com.hvac.simulator.device.port;

import com.hvac.simulator.energy.EnergyType;
import java.util.Objects;

/** 设备对外公开的连接点；水侧用于阻止供水管与回水管被误连。 */
public record PortDefinition(
        String id,
        String displayName,
        EnergyType energyType,
        PortDirection direction,
        WaterSide waterSide,
        PortCardinality cardinality) {

    public PortDefinition {
        requireText(id, "端口编号不能为空");
        requireText(displayName, "端口名称不能为空");
        Objects.requireNonNull(energyType, "端口能源类型不能为空");
        Objects.requireNonNull(direction, "端口方向不能为空");
        Objects.requireNonNull(waterSide, "端口水侧不能为空");
        Objects.requireNonNull(cardinality, "端口连接规则不能为空");
        if (energyType.isWater() == (waterSide == WaterSide.NOT_APPLICABLE)) {
            throw new IllegalArgumentException("水端口必须声明供回水侧，非水端口不得声明供回水侧");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
