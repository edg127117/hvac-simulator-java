package com.hvac.simulator.config;

/** 办公建筑人员、照明、设备和新风的额定内部负荷参数。 */
public record InternalLoad(
        double occupancyPerM2,
        double sensibleWPerPerson,
        double latentWPerPerson,
        double lightingWPerM2,
        double equipmentWPerM2,
        double outdoorAirM3PerHourPerson) {

    public static InternalLoad gaiaDefaults() {
        return new InternalLoad(0.1, 65.0, 55.0, 9.0, 15.0, 30.0);
    }
}
