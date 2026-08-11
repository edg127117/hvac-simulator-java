package com.hvac.simulator.weather;

import com.hvac.simulator.config.SimulationConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Objects;

/** 从冻结的 Python 结果中只读取气象输入，禁止把 Python 计算结果回灌给 Java。 */
public final class BaselineWeatherSource implements WeatherSource {

    private static final String EXPECTED_HEADER = String.join(",",
            "datetime", "T_outdoor", "T_wb", "solar", "T_room", "cooling_load_kW",
            "chiller_power_kW", "chw_pump_power_kW", "cw_pump_power_kW", "ct_fan_power_kW",
            "terminal_fan_power_kW", "total_power_kW", "chiller_PLR", "chiller_COP",
            "T_chw_supply", "T_cw_supply", "pipe_heat_gain_kW");
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String resourceName;

    public BaselineWeatherSource(String resourceName) {
        this.resourceName = Objects.requireNonNull(resourceName, "基准资源名不能为空");
    }

    @Override
    public WeatherSeries load(SimulationConfig config) throws IOException {
        Objects.requireNonNull(config, "仿真配置不能为空");
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IOException("找不到基准气象资源：" + resourceName);
        }

        var points = new ArrayList<WeatherPoint>(config.expectedSteps());
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!EXPECTED_HEADER.equals(header)) {
                throw new IOException("基准 CSV 表头不匹配：" + resourceName);
            }

            String line;
            int dataIndex = 0;
            while ((line = reader.readLine()) != null) {
                int fileLine = dataIndex + 2;
                String[] fields = line.split(",", -1);
                if (fields.length != 17) {
                    throw new IOException("基准 CSV 字段数错误：资源=" + resourceName + "，行=" + fileLine);
                }
                LocalDateTime expectedTimestamp = config.start().plusMinutes((long) dataIndex * config.dtMinutes());
                LocalDateTime timestamp = parseTimestamp(fields[0], fileLine);
                if (!timestamp.equals(expectedTimestamp)) {
                    throw new IOException("基准时间不连续：资源=" + resourceName + "，行=" + fileLine
                            + "，预期=" + expectedTimestamp + "，实际=" + timestamp);
                }
                points.add(new WeatherPoint(
                        timestamp,
                        parseFinite(fields[1], "T_outdoor", fileLine),
                        parseFinite(fields[2], "T_wb", fileLine),
                        parseFinite(fields[3], "solar", fileLine)));
                dataIndex++;
            }
        }
        if (points.size() != config.expectedSteps()) {
            throw new IOException("基准 CSV 行数错误：预期=" + config.expectedSteps() + "，实际=" + points.size());
        }
        return new WeatherSeries(points);
    }

    private LocalDateTime parseTimestamp(String value, int fileLine) throws IOException {
        try {
            return LocalDateTime.parse(value, TIMESTAMP_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IOException("基准时间格式错误：资源=" + resourceName + "，行=" + fileLine, exception);
        }
    }

    private double parseFinite(String value, String field, int fileLine) throws IOException {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException("非有限值");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IOException("基准数值错误：资源=" + resourceName + "，行=" + fileLine
                    + "，字段=" + field + "，值=" + value, exception);
        }
    }
}
