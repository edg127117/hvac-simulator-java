package com.hvac.simulator.device.parameter;

public enum ParameterUsage {
    CONFIGURABLE_CALCULATION(true, true),
    FIXED_CALCULATION(false, true),
    NOT_USED_IN_CALCULATION(false, false);

    private final boolean modifiable;
    private final boolean usedInCalculation;

    ParameterUsage(boolean modifiable, boolean usedInCalculation) {
        this.modifiable = modifiable;
        this.usedInCalculation = usedInCalculation;
    }

    public boolean modifiable() {
        return modifiable;
    }

    public boolean usedInCalculation() {
        return usedInCalculation;
    }
}
