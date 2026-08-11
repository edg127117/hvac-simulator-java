package com.hvac.simulator.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hvac.simulator.config.SimulationConfig;
import com.hvac.simulator.measurement.Gaia11MeasurementModel;
import com.hvac.simulator.measurement.SeededRandomDrawSource;
import com.hvac.simulator.model.BuildingThermalModel;
import com.hvac.simulator.model.HvacSystem;
import com.hvac.simulator.simulation.Gaia11Simulator;
import com.hvac.simulator.weather.Gaia11BaselineWeatherSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelReleaseCatalogTest {
    private final ModelReleaseCatalog catalog = new ModelReleaseCatalog();

    @Test
    void isolatesVersionsAndPublishesRealDefaults() {
        var gaia10 = catalog.release(ModelVersion.GAIA_1_0);
        var gaia11 = catalog.release(ModelVersion.GAIA_1_1);

        assertEquals(17, gaia10.outputFieldCount());
        assertEquals(30, gaia11.outputFieldCount());
        assertFalse(gaia10.parameters().stream().anyMatch(p -> p.code().startsWith("measurement.")));
        assertEquals(4, gaia11.parameters().stream().filter(p -> p.code().startsWith("measurement.")).count());
        assertFalse(gaia11.parameters().stream().anyMatch(p -> p.code().equalsIgnoreCase("R_wall")));
        assertEquals(0.5, parameter(gaia11, "measurement.flowNoiseStdPercent").defaultValue());
        assertEquals(25.0, parameter(gaia11, "hvac.coolingSetpointC").defaultValue());
    }

    @Test
    void rejectsUnknownCrossVersionAndInvalidOverrides() {
        assertThrows(IllegalArgumentException.class, () -> catalog.applyOverrides(
                ModelVersion.GAIA_1_0, Map.of("measurement.sensorBias", 1.0)));
        assertThrows(IllegalArgumentException.class, () -> catalog.applyOverrides(
                ModelVersion.GAIA_1_1, Map.of("unknown", 1.0)));
        assertThrows(IllegalArgumentException.class, () -> catalog.applyOverrides(
                ModelVersion.GAIA_1_1, Map.of("building.floorCount", 1.5)));
    }

    @Test
    void overridesAreUsedByActualCalculation() throws Exception {
        double defaultLoad = totalCooling(Map.of());
        double changedLoad = totalCooling(Map.of("hvac.coolingSetpointC", 20.0));

        assertNotEquals(defaultLoad, changedLoad);
        assertTrue(changedLoad > defaultLoad);
    }

    private double totalCooling(Map<String, Double> overrides) throws Exception {
        var snapshot = catalog.applyOverrides(ModelVersion.GAIA_1_1, overrides);
        SimulationConfig config = SimulationConfig.gaiaDemo(99L);
        var result = new Gaia11Simulator(
                config,
                new BuildingThermalModel(snapshot.building(), snapshot.internalLoad()),
                new HvacSystem(snapshot.hvac()),
                new Gaia11MeasurementModel(snapshot.measurement()),
                new SeededRandomDrawSource(config.randomSeed()))
                .run(new Gaia11BaselineWeatherSource(
                        "gaia-baseline/gaia-1.1/python-weather.csv").load(config));
        return result.steps().stream().mapToDouble(step -> step.coolingLoadKw()).sum();
    }

    private ModelParameterDescriptor parameter(ModelReleaseDescriptor release, String code) {
        return release.parameters().stream().filter(parameter -> parameter.code().equals(code))
                .findFirst().orElseThrow();
    }
}
