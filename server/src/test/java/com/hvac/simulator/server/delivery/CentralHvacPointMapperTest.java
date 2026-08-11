package com.hvac.simulator.server.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hvac.simulator.config.BuildingEnvelope;
import com.hvac.simulator.config.Gaia11MeasurementParameters;
import com.hvac.simulator.config.HvacParameters;
import com.hvac.simulator.config.InternalLoad;
import com.hvac.simulator.config.SimulationConfig;
import com.hvac.simulator.measurement.FrozenRandomDrawSource;
import com.hvac.simulator.measurement.Gaia11MeasurementModel;
import com.hvac.simulator.model.BuildingThermalModel;
import com.hvac.simulator.model.HvacSystem;
import com.hvac.simulator.simulation.Gaia11SimulationStep;
import com.hvac.simulator.simulation.Gaia11Simulator;
import com.hvac.simulator.weather.Gaia11BaselineWeatherSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class CentralHvacPointMapperTest {
    private final CentralHvacPointMapper mapper = new CentralHvacPointMapper();

    @Test
    void mapsOnlyFourMeasuredWcrPointsAndPreservesCop() throws Exception {
        Gaia11SimulationStep step = runningStep();

        List<CentralHvacPoint> points = mapper.map(step, "BLD001", "WCR1", 1_785_398_400_000L);

        assertEquals(List.of("WCR1_TWin", "WCR1_TWout", "WCR1_Flow", "WCR1_PPE"),
                points.stream().map(CentralHvacPoint::pointCode).toList());
        assertEquals(step.chilledWaterFlowSensorM3PerSecond() * 3_600.0, points.get(2).value());
        assertEquals(step.measuredCop(), mapper.centralPlatformCop(points),
                Math.max(1e-8, Math.abs(step.measuredCop()) * 1e-8));
        assertFalse(points.stream().anyMatch(point -> point.pointCode().matches(".*(Ua|Ia|Pa|PIn|POut).*")));

        MqttPublishMessage message = mapper.message("device/data/up", points.getFirst());
        assertEquals(1, message.qos());
        assertFalse(message.retained());
        assertTrue(message.payload().contains("\"buildingId\":\"BLD001\""));
        assertTrue(message.payload().contains("\"timestamp\":1785398400000"));
    }

    private Gaia11SimulationStep runningStep() throws Exception {
        SimulationConfig config = SimulationConfig.gaiaDemo(20240810L);
        var result = new Gaia11Simulator(
                config,
                new BuildingThermalModel(BuildingEnvelope.gaiaDefaults(), InternalLoad.gaiaDefaults()),
                new HvacSystem(HvacParameters.gaiaDefaults()),
                new Gaia11MeasurementModel(Gaia11MeasurementParameters.gaiaDefaults()),
                new FrozenRandomDrawSource("gaia-baseline/gaia-1.1/python-random-draws.csv"))
                .run(new Gaia11BaselineWeatherSource(
                        "gaia-baseline/gaia-1.1/python-weather.csv").load(config));
        return result.steps().stream().filter(step -> step.chillerPowerKw() > 0.0).findFirst().orElseThrow();
    }
}
