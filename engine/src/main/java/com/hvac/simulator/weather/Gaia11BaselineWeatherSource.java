package com.hvac.simulator.weather;

import com.hvac.simulator.config.SimulationConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Objects;

/** 只从 Gaia 1.1 冻结资产读取四字段气象输入。 */
public final class Gaia11BaselineWeatherSource implements WeatherSource {
    private static final String HEADER = "datetime,T_outdoor,T_wb,solar";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final String resourceName;

    public Gaia11BaselineWeatherSource(String resourceName) {
        this.resourceName = Objects.requireNonNull(resourceName, "气象资源名不能为空");
    }

    @Override
    public WeatherSeries load(SimulationConfig config) throws IOException {
        var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IOException("找不到 Gaia 1.1 气象资源：" + resourceName);
        }
        var points = new ArrayList<WeatherPoint>(config.expectedSteps());
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            if (!HEADER.equals(reader.readLine())) {
                throw new IOException("Gaia 1.1 气象 CSV 表头不匹配");
            }
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                if (fields.length != 4) {
                    throw new IOException("Gaia 1.1 气象 CSV 字段数错误，行=" + (index + 2));
                }
                try {
                    LocalDateTime timestamp = LocalDateTime.parse(fields[0], FORMAT);
                    LocalDateTime expected = config.start().plusMinutes((long) index * config.dtMinutes());
                    if (!timestamp.equals(expected)) {
                        throw new IOException("Gaia 1.1 气象时间不连续，行=" + (index + 2));
                    }
                    points.add(new WeatherPoint(
                            timestamp, finite(fields[1]), finite(fields[2]), finite(fields[3])));
                } catch (RuntimeException exception) {
                    throw new IOException("Gaia 1.1 气象数值错误，行=" + (index + 2), exception);
                }
                index++;
            }
        }
        if (points.size() != config.expectedSteps()) {
            throw new IOException("Gaia 1.1 气象行数错误：" + points.size());
        }
        return new WeatherSeries(points);
    }

    private double finite(String value) {
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed)) {
            throw new NumberFormatException("非有限值");
        }
        return parsed;
    }
}
