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
        PortCardinality cardinality,
        PortValueSpec valueSpec) {

    public PortDefinition(
            String id,
            String displayName,
            EnergyType energyType,
            PortDirection direction,
            WaterSide waterSide,
            PortCardinality cardinality) {
        this(id, displayName, energyType, direction, waterSide, cardinality, defaultSpec(energyType));
    }

    public PortDefinition {
        requireText(id, "端口编号不能为空");
        requireText(displayName, "端口名称不能为空");
        Objects.requireNonNull(energyType, "端口能源类型不能为空");
        Objects.requireNonNull(direction, "端口方向不能为空");
        Objects.requireNonNull(waterSide, "端口水侧不能为空");
        Objects.requireNonNull(cardinality, "端口连接规则不能为空");
        Objects.requireNonNull(valueSpec, "端口运行值规格不能为空");
        if (energyType.isWater() == (waterSide == WaterSide.NOT_APPLICABLE)) {
            throw new IllegalArgumentException("水端口必须声明供回水侧，非水端口不得声明供回水侧");
        }
        if (!valueSpec.supportsEnergyType(energyType)) {
            throw new IllegalArgumentException("端口能源类型与运行值规格不兼容");
        }
    }

    private static PortValueSpec defaultSpec(EnergyType energyType) {
        Objects.requireNonNull(energyType, "端口能源类型不能为空");
        if (energyType == EnergyType.ELECTRICITY) {
            return ElectricalPortValueSpec.INSTANCE;
        }
        if (energyType.isWater()) {
            return new WaterPortValueSpec(com.hvac.simulator.energy.runtime.FluidMedium.WATER);
        }
        throw new IllegalArgumentException("控制信号端口必须显式声明运行值规格");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
