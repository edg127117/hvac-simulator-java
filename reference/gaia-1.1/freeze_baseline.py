"""运行未修改的 Gaia 1.1 原件并冻结可供 Java 忠实转换的证据。"""

from __future__ import annotations

import hashlib
import importlib.util
import os
import shutil
import sys
import tempfile
from datetime import datetime, timedelta
from pathlib import Path

os.environ.setdefault("MPLBACKEND", "Agg")
os.environ.setdefault(
    "MPLCONFIGDIR",
    str(Path(__file__).resolve().parents[2] / ".venv-gaia11" / "matplotlib-cache"),
)

import numpy as np
import pandas as pd


SEED = 20240810
START = datetime(2024, 7, 1, 0, 0)
END = datetime(2024, 7, 7, 23, 59)
STEPS = 10_080
SOURCE_SHA256 = "4E71C1FFECBEA97057F9ED34153441EF49B2F655DFF36651FE4C9214A180C370"
RESULT_COLUMNS = [
    "datetime", "T_outdoor", "T_wb", "solar", "T_room", "cooling_load_kW",
    "chiller_power_kW", "chw_pump_power_kW", "cw_pump_power_kW",
    "ct_fan_power_kW", "terminal_fan_power_kW", "total_power_kW",
    "chiller_PLR", "chiller_COP", "chw_flow_rate", "cw_flow_rate",
    "T_chw_supply", "T_chw_return", "T_cw_supply", "T_cw_return",
    "pipe_heat_gain_kW", "chw_flow_sensor", "cw_flow_sensor",
    "T_chw_supply_sensor", "T_chw_return_sensor", "T_cw_supply_sensor",
    "T_cw_return_sensor", "chiller_power_true_kW", "measured_cooling_kW",
    "measured_COP",
]
RANDOM_COLUMNS = [
    "datetime", "power_uniform_unit", "chw_flow_normal", "cw_flow_normal",
    "T_chw_supply_normal", "T_chw_return_normal", "T_cw_supply_normal",
    "T_cw_return_normal",
]

SCRIPT_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_DIR.parents[1]
SOURCE = SCRIPT_DIR / "Gaia1.1.py"
OFFICIAL_OUTPUTS = {
    "weather": REPOSITORY_ROOT / "engine/src/main/resources/gaia-baseline/gaia-1.1/python-weather.csv",
    "random": REPOSITORY_ROOT / "engine/src/main/resources/gaia-baseline/gaia-1.1/python-random-draws.csv",
    "results": REPOSITORY_ROOT / "engine/src/test/resources/gaia-baseline/gaia-1.1/python-results.csv",
    "plot": SCRIPT_DIR / "python-reference-plot.png",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def load_reference_module():
    spec = importlib.util.spec_from_file_location("gaia11_reference", SOURCE)
    if spec is None or spec.loader is None:
        raise RuntimeError("无法装载 Gaia 1.1 原始参考文件")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def capture_reference_run(module):
    weather_capture: dict[str, np.ndarray] = {}
    normal_draws: list[float] = []
    power_draws: dict[int, float] = {}
    measurement_phase = False

    original_weather = module.generate_annual_weather
    original_normal = module.np.random.normal
    original_uniform = module.np.random.uniform

    def traced_weather(start, end, dt_minutes):
        nonlocal measurement_phase
        values = original_weather(start, end, dt_minutes)
        weather_capture["T_outdoor"], weather_capture["T_wb"], weather_capture["solar"] = values
        measurement_phase = True
        return values

    def traced_normal(loc=0.0, scale=1.0, size=None):
        value = original_normal(loc, scale, size)
        if measurement_phase:
            if size is not None or not np.isscalar(value) or scale == 0:
                raise AssertionError("测量阶段出现非标量或零尺度高斯抽样")
            normal_draws.append(float((value - loc) / scale))
        return value

    def traced_uniform(low=0.0, high=1.0, size=None):
        value = original_uniform(low, high, size)
        if measurement_phase:
            if size is not None or not np.isscalar(value) or high != -low or high <= 0:
                raise AssertionError("测量阶段出现非电能表均匀抽样")
            step = len(normal_draws) // 6
            if step in power_draws:
                raise AssertionError(f"第 {step} 步重复消费电能表随机数")
            power_draws[step] = float(value / high)
        return value

    module.generate_annual_weather = traced_weather
    module.np.random.normal = traced_normal
    module.np.random.uniform = traced_uniform
    module.SIM_START = START
    module.SIM_END = END
    module.PRINT_INTERVAL = STEPS + 1

    np.random.seed(SEED)
    simulator = module.Simulator(
        module.BuildingThermal(module.BuildingEnvelope(), module.InternalLoad()),
        module.HVACSystem(module.HVACParameters()),
        module.WeatherData(),
    )
    result = simulator.run()

    if list(result.columns) != RESULT_COLUMNS:
        raise AssertionError(f"30 字段顺序变化: {list(result.columns)}")
    if len(result) != STEPS or len(normal_draws) != STEPS * 6:
        raise AssertionError("仿真步数或每步六次传感器抽样不完整")

    datetimes = [START + timedelta(minutes=step) for step in range(STEPS)]
    weather = pd.DataFrame({"datetime": datetimes, **weather_capture})
    random_rows = []
    for step, timestamp in enumerate(datetimes):
        draws = normal_draws[step * 6:(step + 1) * 6]
        random_rows.append([timestamp, power_draws.get(step), *draws])
        if result.iloc[step]["chiller_power_true_kW"] <= 0 and step in power_draws:
            raise AssertionError(f"第 {step} 步停机时错误消费电能表随机数")
        if result.iloc[step]["chiller_power_true_kW"] > 0 and step not in power_draws:
            raise AssertionError(f"第 {step} 步运行时缺少电能表随机数")
    random = pd.DataFrame(random_rows, columns=RANDOM_COLUMNS)
    return weather, random, result


def render_plot(module, result: pd.DataFrame, path: Path) -> None:
    plt = module.plt
    plt.close("all")
    plt.figure(figsize=(14, 14))
    plt.subplot(5, 1, 1)
    plt.plot(result["datetime"], result["T_room"], label="Room Temperature")
    plt.plot(result["datetime"], result["T_outdoor"], label="Outdoor Temperature", alpha=0.6)
    plt.legend()
    plt.ylabel("Temperature (℃)")
    plt.title("Building Thermal Environment & System Performance (with Instrument Measurements)")

    plt.subplot(5, 1, 2)
    plt.plot(result["datetime"], result["cooling_load_kW"], label="Cooling Load")
    plt.plot(result["datetime"], result["total_power_kW"], label="System Total Power (metered)")
    plt.legend()
    plt.ylabel("Power (kW)")

    plt.subplot(5, 1, 3)
    plt.plot(result["datetime"], result["chiller_power_true_kW"], "--", label="Chiller Power (theoretical)", alpha=0.7)
    plt.plot(result["datetime"], result["chiller_power_kW"], label="Chiller Power (meter)", color="red")
    plt.legend()
    plt.ylabel("Chiller Power (kW)")

    plt.subplot(5, 1, 4)
    plt.plot(result["datetime"], result["chiller_COP"], label="COP (theoretical)", alpha=0.7)
    plt.plot(result["datetime"], result["measured_COP"], label="COP (measured)", color="green")
    plt.legend()
    plt.ylabel("COP")
    plt.ylim(0, max(result["chiller_COP"].max(), result["measured_COP"].max()) * 1.1)

    plt.subplot(5, 1, 5)
    plt.plot(result["datetime"], result["T_chw_supply_sensor"], label="CHW Supply (sensor)", color="blue")
    plt.plot(result["datetime"], result["T_chw_return_sensor"], label="CHW Return (sensor)", color="cyan")
    plt.plot(result["datetime"], result["T_cw_supply_sensor"], label="CW Supply (sensor)", color="red")
    plt.plot(result["datetime"], result["T_cw_return_sensor"], label="CW Return (sensor)", color="orange")
    plt.legend()
    plt.ylabel("Water Temperature (℃)")
    plt.xlabel("Time")
    plt.tight_layout()
    plt.savefig(path, dpi=100, metadata={"Software": "Gaia 1.1 baseline freezer"})
    plt.close("all")


def generate_into(directory: Path) -> dict[str, Path]:
    module = load_reference_module()
    weather, random, results = capture_reference_run(module)
    outputs = {name: directory / target.name for name, target in OFFICIAL_OUTPUTS.items()}
    weather.to_csv(outputs["weather"], index=False, float_format="%.17g", date_format="%Y-%m-%d %H:%M:%S")
    random.to_csv(outputs["random"], index=False, float_format="%.17g", date_format="%Y-%m-%d %H:%M:%S")
    results.to_csv(outputs["results"], index=False, float_format="%.17g", date_format="%Y-%m-%d %H:%M:%S")
    render_plot(module, results, outputs["plot"])
    return outputs


def write_manifest() -> None:
    entries = {"Gaia1.1.py": SOURCE, "requirements.txt": SCRIPT_DIR / "requirements.txt", **OFFICIAL_OUTPUTS}
    lines = [f"seed={SEED}", f"start={START:%Y-%m-%d %H:%M:%S}", f"end={END:%Y-%m-%d %H:%M:%S}", f"steps={STEPS}"]
    lines.extend(f"sha256 {name} {sha256(path)}" for name, path in entries.items())
    (SCRIPT_DIR / "baseline-manifest.txt").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def main() -> None:
    if sha256(SOURCE) != SOURCE_SHA256:
        raise SystemExit("Gaia1.1.py 字节哈希变化，拒绝冻结")
    with tempfile.TemporaryDirectory(prefix="gaia11-freeze-") as temp:
        generated = generate_into(Path(temp))
        missing = [name for name, path in OFFICIAL_OUTPUTS.items() if not path.exists()]
        if missing:
            for target in OFFICIAL_OUTPUTS.values():
                target.parent.mkdir(parents=True, exist_ok=True)
            for name, temporary in generated.items():
                shutil.copyfile(temporary, OFFICIAL_OUTPUTS[name])
            print("已创建 Gaia 1.1 冻结基准")
        else:
            mismatches = [name for name, temporary in generated.items() if temporary.read_bytes() != OFFICIAL_OUTPUTS[name].read_bytes()]
            if mismatches:
                raise SystemExit(f"重复运行不一致: {', '.join(mismatches)}")
            print("重复运行与冻结基准逐字节一致")
    write_manifest()
    for name, path in OFFICIAL_OUTPUTS.items():
        print(f"{name}: {sha256(path)}")


if __name__ == "__main__":
    main()
