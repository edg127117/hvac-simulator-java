package com.hvac.simulator.weather;

import com.hvac.simulator.config.SimulationConfig;
import java.io.IOException;

/** 为仿真主链提供按时间排序的干球、湿球和太阳辐射序列。 */
@FunctionalInterface
public interface WeatherSource {
    WeatherSeries load(SimulationConfig config) throws IOException;
}
