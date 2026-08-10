package com.hvac.simulator.device.runtime;

/** 稳定错误编码及对应中文标题，供日志和未来界面共同使用。 */
public enum DeviceCalculationErrorCode {
    MISSING_INPUT("缺少输入"),
    INCOMPATIBLE_VALUE_TYPE("端口值类型不兼容"),
    INCOMPATIBLE_UNIT("单位不兼容"),
    INCOMPATIBLE_MEDIUM("介质不兼容"),
    NON_FINITE_VALUE("数值不是有限值"),
    DIVISION_BY_ZERO("计算发生除零"),
    NUMERIC_OUT_OF_RANGE("数值超出范围"),
    UNSUPPORTED_TIME_STEP("不支持的计算步长");

    private final String title;

    DeviceCalculationErrorCode(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
