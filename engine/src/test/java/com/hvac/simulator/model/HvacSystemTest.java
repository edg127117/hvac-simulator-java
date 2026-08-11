package com.hvac.simulator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hvac.simulator.config.HvacParameters;
import org.junit.jupiter.api.Test;

class HvacSystemTest {

    private final HvacSystem system = new HvacSystem(HvacParameters.gaiaDefaults());

    @Test
    void noDemandStopsAllEquipmentAndSetsCopToZero() {
        var result = system.simulate(0.0, 25.0, 10.0, 5.0, 60.0, 12.0);

        assertEquals(0.0, result.systemTotalPowerKw());
        assertEquals(0.0, result.chillerCop());
        assertEquals(7.0, result.chilledWaterSupplyC());
        assertEquals(32.0, result.coolingWaterSupplyC());
    }

    @Test
    void chillerClampsPlrToGaiaMinimum() {
        var result = system.calculateChiller(10.0, 32.0, 60.0);

        assertEquals(0.1, result.plr());
    }

    @Test
    void ratedChillerUsesRatedCop() {
        var result = system.calculateChiller(1_400.0, 37.0, 60.0);

        assertEquals(1.0, result.plr(), 1e-9);
        assertEquals(6.0, result.cop(), 1e-9);
        assertEquals(233.33333333333334, result.powerKw(), 1e-9);
    }

    @Test
    void variablePumpPreservesGaiaRatedPoint() {
        var pump = HvacParameters.gaiaDefaults().chilledWaterPump();
        double actual = system.calculatePumpPower(200.0 / 3_600.0, pump, true);

        assertEquals(22.6074074074074, actual, 1e-9);
    }

    @Test
    void coolingTowerPreservesRatedPoint() {
        var result = system.calculateCoolingTower(1_633.3333333333333, 28.0);

        assertEquals(32.0, result.coolingWaterOutletC(), 1e-9);
        assertEquals(7.5, result.fanPowerKw(), 1e-9);
    }

    @Test
    void coldPipeUsesOriginalNegativeHeatSign() {
        var pipe = HvacParameters.gaiaDefaults().chilledWaterPipe();
        double actual = system.calculatePipeHeatLoss(7.0, 28.0, pipe);

        assertEquals(-3_204.44705304933, actual, 1e-9);
    }
}
