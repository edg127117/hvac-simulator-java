package com.hvac.simulator.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.hvac.simulator.weather.Gaia11BaselineWeatherSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class Gaia11ParityTest {
    private static final String WEATHER = "gaia-baseline/gaia-1.1/python-weather.csv";
    private static final String RANDOM = "gaia-baseline/gaia-1.1/python-random-draws.csv";
    private static final String EXPECTED = "gaia-baseline/gaia-1.1/python-results.csv";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    void matchesAllThirtyPythonFieldsForEveryMinute() throws Exception {
        SimulationConfig config = SimulationConfig.gaiaDemo(20240810L);
        var result = new Gaia11Simulator(
                config,
                new BuildingThermalModel(BuildingEnvelope.gaiaDefaults(), InternalLoad.gaiaDefaults()),
                new HvacSystem(HvacParameters.gaiaDefaults()),
                new Gaia11MeasurementModel(Gaia11MeasurementParameters.gaiaDefaults()),
                new FrozenRandomDrawSource(RANDOM))
                .run(new Gaia11BaselineWeatherSource(WEATHER).load(config));

        var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(EXPECTED);
        assertTrue(stream != null, "缺少 Python 30 字段基准");
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            assertEquals(Gaia11SimulationStep.CSV_HEADER, reader.readLine());
            for (int index = 0; index < result.steps().size(); index++) {
                String line = reader.readLine();
                assertTrue(line != null, "Python 基准提前结束，步号=" + index);
                String[] fields = line.split(",", -1);
                assertEquals(30, fields.length);
                Gaia11SimulationStep actual = result.steps().get(index);
                assertEquals(LocalDateTime.parse(fields[0], FORMAT), actual.timestamp());
                double[] values = values(actual);
                for (int field = 1; field < fields.length; field++) {
                    double expected = Double.parseDouble(fields[field]);
                    double floor = highMagnitudeField(field) ? 1e-8 : 1e-9;
                    double tolerance = Math.max(floor, Math.abs(expected) * 1e-9);
                    assertEquals(expected, values[field - 1], tolerance,
                            "步号=" + index + "，字段=" + Gaia11SimulationStep.CSV_HEADER.split(",")[field]);
                }
            }
            assertEquals(null, reader.readLine(), "Python 基准存在额外数据行");
        }
    }

    private boolean highMagnitudeField(int field) {
        return field == 3 || (field >= 5 && field <= 11) || field == 20 || field == 27 || field == 28;
    }

    private double[] values(Gaia11SimulationStep s) {
        return new double[] {
            s.outdoorC(), s.wetBulbC(), s.solarGlobalWPerM2(), s.roomC(), s.coolingLoadKw(),
            s.chillerPowerKw(), s.chilledWaterPumpPowerKw(), s.coolingWaterPumpPowerKw(),
            s.coolingTowerFanPowerKw(), s.terminalFanPowerKw(), s.totalPowerKw(), s.chillerPlr(),
            s.chillerCop(), s.chilledWaterFlowM3PerSecond(), s.coolingWaterFlowM3PerSecond(),
            s.chilledWaterSupplyC(), s.chilledWaterReturnC(), s.coolingWaterSupplyC(),
            s.coolingWaterReturnC(), s.pipeHeatGainKw(), s.chilledWaterFlowSensorM3PerSecond(),
            s.coolingWaterFlowSensorM3PerSecond(), s.chilledWaterSupplySensorC(),
            s.chilledWaterReturnSensorC(), s.coolingWaterSupplySensorC(),
            s.coolingWaterReturnSensorC(), s.chillerPowerTrueKw(), s.measuredCoolingKw(), s.measuredCop()
        };
    }
}
