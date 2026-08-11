package com.hvac.simulator.measurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hvac.simulator.config.Gaia11MeasurementParameters;
import com.hvac.simulator.model.HvacStepResult;
import org.junit.jupiter.api.Test;

class Gaia11MeasurementModelTest {
    private final Gaia11MeasurementModel model =
            new Gaia11MeasurementModel(Gaia11MeasurementParameters.gaiaDefaults());

    @Test
    void preservesZeroFlowAbsoluteNoiseAndSharedBias() {
        var measured = model.measure(stopped(), new Gaia11RandomDraws(
                null, 1.0, -1.0, 1.0, -1.0, 0.5, -0.5));

        assertEquals(0.5, measured.chilledWaterFlowM3PerSecond());
        assertEquals(-0.5, measured.coolingWaterFlowM3PerSecond());
        assertEquals(7.1, measured.chilledWaterSupplyC(), 1e-15);
        assertEquals(11.9, measured.chilledWaterReturnC(), 1e-15);
        assertEquals(32.05, measured.coolingWaterSupplyC(), 1e-15);
        assertEquals(36.95, measured.coolingWaterReturnC(), 1e-15);
    }

    @Test
    void computesMeasuredCoolingAndCopWithoutChangingOriginalFormula() {
        var measured = new Gaia11MeasuredValues(20.0, 0.01, 0.011, 7.0, 12.0, 32.0, 37.0);

        var derived = model.derive(measured);

        assertEquals(209.0, derived.coolingKw(), 1e-12);
        assertEquals(10.45, derived.cop(), 1e-12);
        assertEquals(0.0, model.derive(new Gaia11MeasuredValues(
                20.0, -0.01, 0.0, 7.0, 12.0, 32.0, 37.0)).coolingKw());
    }

    @Test
    void enforcesConditionalPowerDraw() {
        assertThrows(IllegalArgumentException.class, () -> model.measure(
                stopped(), new Gaia11RandomDraws(0.0, 0, 0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> model.measure(
                running(), new Gaia11RandomDraws(null, 0, 0, 0, 0, 0, 0)));
    }

    private HvacStepResult stopped() {
        return new HvacStepResult(
                0, 0, 0, 0, 0, 0, 7, 12, 32, 37, 0, 0, 0, 0, 0, 0);
    }

    private HvacStepResult running() {
        return new HvacStepResult(
                20, 1, 1, 1, 1, 24, 7, 12, 32, 37, 0.5, 5, 0.01, 0.011, 0, 0);
    }
}
