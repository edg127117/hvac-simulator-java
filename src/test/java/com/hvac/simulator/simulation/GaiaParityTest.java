package com.hvac.simulator.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hvac.simulator.TestFixtures;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class GaiaParityTest {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] NUMERIC_FIELDS = {
        "T_outdoor", "T_wb", "solar", "T_room", "cooling_load_kW", "chiller_power_kW",
        "chw_pump_power_kW", "cw_pump_power_kW", "ct_fan_power_kW", "terminal_fan_power_kW",
        "total_power_kW", "chiller_PLR", "chiller_COP", "T_chw_supply", "T_cw_supply",
        "pipe_heat_gain_kW"
    };

    @Test
    void allRowsAndFieldsMatchFrozenPythonBaseline() throws Exception {
        var actualSteps = TestFixtures.runBaseline().steps();
        var stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("gaia-baseline/python-results.csv");
        assertNotNull(stream);

        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            assertEquals(SimulationStep.CSV_HEADER, reader.readLine());
            for (int index = 0; index < actualSteps.size(); index++) {
                String line = reader.readLine();
                assertNotNull(line, "Python 基准提前结束，索引=" + index);
                String[] fields = line.split(",", -1);
                SimulationStep actual = actualSteps.get(index);
                assertEquals(actual.timestamp().format(TIMESTAMP_FORMAT), fields[0], "时间索引=" + index);
                double[] values = values(actual);
                for (int fieldIndex = 0; fieldIndex < values.length; fieldIndex++) {
                    double expected = Double.parseDouble(fields[fieldIndex + 1]);
                    double tolerance = Math.max(1e-9, Math.abs(expected) * 1e-9);
                    int checkedField = fieldIndex;
                    assertEquals(expected, values[fieldIndex], tolerance,
                            () -> "时间=" + actual.timestamp() + "，字段="
                                    + NUMERIC_FIELDS[checkedField] + "，允许误差=" + tolerance);
                }
            }
            assertEquals(null, reader.readLine(), "Python 基准存在多余数据行");
        }
    }

    private double[] values(SimulationStep step) {
        return new double[] {
            step.outdoorC(), step.wetBulbC(), step.solarGlobalWPerM2(), step.roomC(),
            step.coolingLoadKw(), step.chillerPowerKw(), step.chilledWaterPumpPowerKw(),
            step.coolingWaterPumpPowerKw(), step.coolingTowerFanPowerKw(), step.terminalFanPowerKw(),
            step.totalPowerKw(), step.chillerPlr(), step.chillerCop(), step.chilledWaterSupplyC(),
            step.coolingWaterSupplyC(), step.pipeHeatGainKw()
        };
    }
}
