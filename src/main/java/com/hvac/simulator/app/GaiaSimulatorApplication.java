package com.hvac.simulator.app;

import com.hvac.simulator.config.BuildingEnvelope;
import com.hvac.simulator.config.HvacParameters;
import com.hvac.simulator.config.InternalLoad;
import com.hvac.simulator.config.SimulationConfig;
import com.hvac.simulator.config.WeatherParameters;
import com.hvac.simulator.model.BuildingThermalModel;
import com.hvac.simulator.model.HvacSystem;
import com.hvac.simulator.output.CsvResultWriter;
import com.hvac.simulator.output.GaiaChartRenderer;
import com.hvac.simulator.simulation.SimulationResult;
import com.hvac.simulator.simulation.Simulator;
import com.hvac.simulator.weather.BaselineWeatherSource;
import com.hvac.simulator.weather.SyntheticWeatherGenerator;
import com.hvac.simulator.weather.WeatherSource;
import java.nio.file.Path;

/** Java CLI 装配入口，负责选择气象源、运行仿真并输出 CSV 与中文三联图。 */
public final class GaiaSimulatorApplication {

    private static final String BASELINE_RESOURCE = "gaia-baseline/python-results.csv";

    public static void main(String[] args) {
        try {
            int exitCode = new GaiaSimulatorApplication().run(args);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        } catch (Exception exception) {
            System.err.println("仿真失败：" + exception.getMessage());
            System.exit(1);
        }
    }

    /** 成功返回 0；参数、输入、计算或输出错误均向调用方显式抛出。 */
    public int run(String[] args) throws Exception {
        Options options = parse(args);
        SimulationConfig config = SimulationConfig.gaiaDemo(options.seed());
        WeatherSource weatherSource = switch (options.weatherMode()) {
            case "baseline" -> new BaselineWeatherSource(BASELINE_RESOURCE);
            case "synthetic" -> new SyntheticWeatherGenerator(WeatherParameters.gaiaDefaults());
            default -> throw new IllegalArgumentException("未知气象模式：" + options.weatherMode());
        };
        var building = new BuildingThermalModel(
                BuildingEnvelope.gaiaDefaults(), InternalLoad.gaiaDefaults());
        var hvac = new HvacSystem(HvacParameters.gaiaDefaults());
        SimulationResult result = new Simulator(config, building, hvac).run(weatherSource.load(config));

        Path csv = options.outputDirectory().resolve("hvac_simulation_results.csv");
        Path plot = options.outputDirectory().resolve("simulation_plot.png");
        new CsvResultWriter().write(result, csv);
        new GaiaChartRenderer().write(result, plot);
        System.out.println("仿真完成：模式=" + options.weatherMode()
                + "，步数=" + result.steps().size()
                + "，输出目录=" + options.outputDirectory().toAbsolutePath());
        return 0;
    }

    private Options parse(String[] args) {
        String weatherMode = "baseline";
        long seed = 42L;
        Path outputDirectory = Path.of("output");
        for (String argument : args) {
            if (argument.startsWith("--weather=")) {
                weatherMode = argument.substring("--weather=".length());
            } else if (argument.startsWith("--seed=")) {
                String value = argument.substring("--seed=".length());
                try {
                    seed = Long.parseLong(value);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("随机种子必须是 long 整数：" + value, exception);
                }
            } else if (argument.startsWith("--output=")) {
                String value = argument.substring("--output=".length());
                if (value.isBlank()) {
                    throw new IllegalArgumentException("输出目录不能为空");
                }
                outputDirectory = Path.of(value);
            } else {
                throw new IllegalArgumentException("未知参数：" + argument);
            }
        }
        if (!weatherMode.equals("baseline") && !weatherMode.equals("synthetic")) {
            throw new IllegalArgumentException("未知气象模式：" + weatherMode);
        }
        return new Options(weatherMode, seed, outputDirectory);
    }

    private record Options(String weatherMode, long seed, Path outputDirectory) {}
}
