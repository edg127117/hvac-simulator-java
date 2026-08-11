package com.hvac.simulator.release;

import java.util.Arrays;

/** 正式暴露的 Gaia 模型版本标识。 */
public enum ModelVersion {
    GAIA_1_0("gaia-1.0"),
    GAIA_1_1("gaia-1.1");

    private final String code;

    ModelVersion(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ModelVersion parse(String value) {
        return Arrays.stream(values())
                .filter(version -> version.code.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知模型版本：" + value));
    }
}
