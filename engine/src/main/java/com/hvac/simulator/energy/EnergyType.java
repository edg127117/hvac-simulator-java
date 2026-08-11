package com.hvac.simulator.energy;

/** 拓扑端口共享的能源或信号介质；是否可连接仍由端口方向和水侧共同决定。 */
public enum EnergyType {
    ELECTRICITY(false),
    CHILLED_WATER(true),
    CONDENSER_WATER(true),
    HOT_WATER(true),
    CONTROL_SIGNAL(false);

    private final boolean water;

    EnergyType(boolean water) {
        this.water = water;
    }

    public boolean isWater() {
        return water;
    }
}
