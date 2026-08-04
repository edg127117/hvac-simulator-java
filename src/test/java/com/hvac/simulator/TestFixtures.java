package com.hvac.simulator;

import com.hvac.simulator.config.BuildingEnvelope;
import com.hvac.simulator.config.HvacParameters;
import com.hvac.simulator.config.InternalLoad;
import com.hvac.simulator.config.SimulationConfig;
import com.hvac.simulator.model.BuildingThermalModel;
import com.hvac.simulator.model.HvacSystem;
import com.hvac.simulator.simulation.SimulationResult;
import com.hvac.simulator.simulation.Simulator;
import com.hvac.simulator.weather.BaselineWeatherSource;

public final class TestFixtures {

    private static final SimulationResult BASELINE = createBaseline();

    private TestFixtures() {}

    public static SimulationResult runBaseline() {
        return BASELINE;
    }

    private static SimulationResult createBaseline() {
        try {
            var config = SimulationConfig.gaiaDemo(42L);
            var weather = new BaselineWeatherSource("gaia-baseline/python-results.csv").load(config);
            var building = new BuildingThermalModel(
                    BuildingEnvelope.gaiaDefaults(), InternalLoad.gaiaDefaults());
            var hvac = new HvacSystem(HvacParameters.gaiaDefaults());
            return new Simulator(config, building, hvac).run(weather);
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
