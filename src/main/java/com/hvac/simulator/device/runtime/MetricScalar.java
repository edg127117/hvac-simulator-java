package com.hvac.simulator.device.runtime;

import com.hvac.simulator.energy.runtime.UnitCode;
import java.util.Objects;

/** 设备指标的强类型标量；单位随数值一同传递。 */
public sealed interface MetricScalar permits MetricScalar.DecimalValue,
        MetricScalar.IntegerValue, MetricScalar.BooleanValue, MetricScalar.EnumValue {
    UnitCode unit();

    record DecimalValue(double value, UnitCode unit) implements MetricScalar {
        public DecimalValue {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("指标小数值必须是有限值");
            }
            Objects.requireNonNull(unit, "指标单位不能为空");
        }
    }

    record IntegerValue(long value, UnitCode unit) implements MetricScalar {
        public IntegerValue {
            Objects.requireNonNull(unit, "指标单位不能为空");
        }
    }

    record BooleanValue(boolean value) implements MetricScalar {
        @Override
        public UnitCode unit() {
            return UnitCode.NONE;
        }
    }

    record EnumValue(String value) implements MetricScalar {
        public EnumValue {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("指标枚举值不能为空");
            }
        }

        @Override
        public UnitCode unit() {
            return UnitCode.NONE;
        }
    }
}
