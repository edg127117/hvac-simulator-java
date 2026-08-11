# ==================== 办公建筑中央空调系统全年逐分钟仿真程序（完整仪表+COP计算，支持中文） ====================
# 功能：生成建筑热过程、管网水力热力、冷热源能耗的全方位运行数据，
#       模拟冷冻水/冷却水流量温度传感器、冷机电能表，并基于测量值计算实际运行COP。
# 步长可配置，默认1分钟；输出CSV及可视化图表。

import numpy as np
import pandas as pd
from dataclasses import dataclass, field
import matplotlib.pyplot as plt
from datetime import datetime, timedelta
import warnings
warnings.filterwarnings('ignore')

# ==================== 全局仿真参数 ====================
SIM_START = datetime(2024, 1, 1, 0, 0)          # 仿真开始时间
SIM_END   = datetime(2024, 12, 31, 23, 59)      # 仿真结束时间（全年）
DT_MINUTES = 1                                   # 仿真时间步长（分钟）
PRINT_INTERVAL = 60 * 24 * 30                    # 进度打印间隔（分钟）

# ==================== 设置中文字体 ====================
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei', 'WenQuanYi Micro Hei']
plt.rcParams['axes.unicode_minus'] = False

# ==================== 参数数据类 ====================
@dataclass
class WeatherData:
    """气象参数"""
    lat: float = 30.5
    lon: float = 114.3
    T_db_design: float = 35.0
    T_wb_design: float = 28.0
    T_db: np.ndarray = None
    T_wb: np.ndarray = None
    solar_global: np.ndarray = None

@dataclass
class BuildingEnvelope:
    """建筑围护结构及几何参数"""
    total_area: float = 12000
    conditioned_area: float = 10000
    num_floors: int = 10
    floor_height: float = 3.6
    volume_per_floor: float = 1200 * 3.6
    window_wall_ratio_s: float = 0.5
    window_wall_ratio_n: float = 0.3
    window_wall_ratio_e: float = 0.4
    window_wall_ratio_w: float = 0.4
    U_wall: float = 0.45
    U_roof: float = 0.35
    U_window: float = 2.0
    SHGC: float = 0.35
    C_wall: float = 5000000
    infiltration_ach: float = 0.2

@dataclass
class InternalLoad:
    """内部负荷及时间表"""
    occ_density: float = 0.1
    sensible_per_person: float = 65
    latent_per_person: float = 55
    lighting_power_density: float = 9.0
    equipment_power_density: float = 15.0
    outdoor_air_rate: float = 30
    schedule_weekday: np.ndarray = None
    schedule_weekend: np.ndarray = None

@dataclass
class HVACParameters:
    """中央空调系统详细技术参数"""
    chiller_type: str = "离心式冷水机组"
    chiller_rated_capacity: float = 1400
    chiller_rated_COP: float = 6.0
    chiller_rated_CHW_T_supply: float = 7
    chiller_rated_CHW_T_return: float = 12
    chiller_rated_CW_T_supply: float = 32
    chiller_rated_CW_T_return: float = 37
    plr_curve_a: float = 0.2
    plr_curve_b: float = 0.8
    plr_curve_c: float = 0.0

    chw_pipe_network: dict = field(default_factory=lambda: {
        "topology": "支状同程式",
        "DN_main": 200,
        "inner_dia_main": 0.207,
        "length_main": 200,
        "abs_roughness": 0.000046,
        "insulation_thickness": 0.03,
        "insulation_conductivity": 0.034,
        "pipe_material": "碳钢无缝钢管"
    })

    cw_pipe_network: dict = field(default_factory=lambda: {
        "DN_main": 250,
        "inner_dia_main": 0.257,
        "length_main": 200,
        "insulation_thickness": 0.03,
        "insulation_conductivity": 0.034,
    })

    chw_pump_rated_flow: float = 200
    chw_pump_rated_head: float = 28
    chw_pump_rated_efficiency: float = 0.75
    chw_pump_motor_efficiency: float = 0.9
    chw_pump_control: str = "最不利末端恒压差控制(变频)"

    cw_pump_rated_flow: float = 220
    cw_pump_rated_head: float = 25
    cw_pump_rated_efficiency: float = 0.78
    cw_pump_motor_efficiency: float = 0.9

    ct_rated_flow: float = 220
    ct_rated_fan_power: float = 7.5
    ct_rated_approach: float = 4.0

    terminal_type: str = "FCU"
    fcu_count: int = 200
    fcu_rated_air_flow: float = 0.15
    fcu_rated_fan_power: float = 0.05
    fcu_rated_water_flow: float = 0.2
    fcu_rated_pressure_drop: float = 30

    cooling_setpoint: float = 25.0
    heating_setpoint: float = 20.0
    deadband: float = 2.0
    chw_reset_schedule: str = "固定7℃"

    # 传感器/仪表噪声参数
    flow_noise_std: float = 0.5          # 流量传感器噪声标准差（%）
    temperature_noise_std: float = 0.1   # 温度传感器噪声标准差（℃）
    sensor_bias: float = 0.0
    power_meter_accuracy: float = 0.5    # 电能表精度等级 0.5级

# ==================== 气象数据生成器 ====================
def generate_annual_weather(start, end, dt_minutes):
    """生成全年逐分钟合成气象数据"""
    total_minutes = int((end - start).total_seconds() / 60) + 1
    t = np.arange(total_minutes, dtype=float)
    days_of_year = (t * dt_minutes) / (24 * 60)

    T_annual = 18 - 12 * np.cos(2 * np.pi * (days_of_year - 15) / 365)
    hours = (t * dt_minutes / 60) % 24
    T_diurnal = 5 * np.sin(2 * np.pi * (hours - 14) / 24)
    T_db = T_annual + T_diurnal + 0.8 * np.random.randn(len(hours))

    season_factor = (np.sin(2 * np.pi * (days_of_year - 170) / 365) * 0.5 + 0.5)
    wet_depression = 3 + 5 * season_factor
    T_wb = T_db - wet_depression + 0.5 * np.random.randn(len(hours))

    declination = 23.45 * np.sin(2 * np.pi * (284 + days_of_year) / 365)
    hour_angle = (hours - 12) * 15
    lat_rad = np.radians(30.5)
    dec_rad = np.radians(declination)
    ha_rad = np.radians(hour_angle)
    sin_altitude = np.sin(lat_rad) * np.sin(dec_rad) + np.cos(lat_rad) * np.cos(dec_rad) * np.cos(ha_rad)
    altitude = np.arcsin(np.clip(sin_altitude, 0, 1))
    solar_constant = 1367
    air_mass = 1 / np.clip(sin_altitude, 0.01, 1)
    I_direct = solar_constant * np.power(0.7, air_mass) * sin_altitude
    I_diffuse = 0.3 * solar_constant * sin_altitude
    I_global = I_direct + I_diffuse
    I_global[altitude <= 0] = 0
    I_global *= np.random.uniform(0.6, 1.2, len(hours))
    I_global = np.clip(I_global, 0, None)
    return T_db, T_wb, I_global

# ==================== 建筑热平衡模型 ====================
class BuildingThermal:
    """一阶RC建筑动态热模型"""
    def __init__(self, env: BuildingEnvelope, internal: InternalLoad):
        self.env = env
        self.internal = internal
        wall_area = 3000
        win_area = 1500
        roof_area = 1200
        self.UA_total = env.U_wall * wall_area + env.U_roof * roof_area + env.U_window * win_area
        self.R_equiv = 1 / self.UA_total if self.UA_total != 0 else 0.1
        self.C = env.C_wall
        self.conditioned_volume = env.conditioned_area * env.floor_height * env.num_floors
        self.air_density = 1.2
        self.air_cp = 1005
        self.latent_heat_vap = 2450e3
        hours = np.arange(24)
        weekday = np.zeros(24)
        weekday[8:18] = 1.0
        weekend = np.zeros(24)
        weekend[10:16] = 0.5
        self.schedule_weekday = weekday
        self.schedule_weekend = weekend

    def get_internal_gains(self, timestep_hour, is_weekday):
        hour = int(timestep_hour % 24)
        coeff = self.schedule_weekday[hour] if is_weekday else self.schedule_weekend[hour]
        area = self.env.conditioned_area
        occ = area * self.internal.occ_density
        sensible_occ = occ * self.internal.sensible_per_person * coeff
        latent_occ = occ * self.internal.latent_per_person * coeff
        light = area * self.internal.lighting_power_density * coeff
        equip = area * self.internal.equipment_power_density * coeff
        return sensible_occ + light + equip, latent_occ

    def step(self, T_indoor, T_outdoor, solar_gain, timestep_hour, is_weekday,
             q_sensible_supply, dt_seconds):
        internal_sensible, _ = self.get_internal_gains(timestep_hour, is_weekday)
        win_area = 1500
        solar_heat = win_area * self.env.SHGC * solar_gain
        envelope_heat = (T_outdoor - T_indoor) * self.UA_total
        infiltration_flow = self.env.infiltration_ach * self.conditioned_volume / 3600
        infiltration_sensible = self.air_density * self.air_cp * infiltration_flow * (T_outdoor - T_indoor)
        net_heat = internal_sensible + solar_heat + envelope_heat + infiltration_sensible + q_sensible_supply
        dT = net_heat * dt_seconds / self.C
        return T_indoor + dT

# ==================== 中央空调系统模型 ====================
class HVACSystem:
    """冷热源、水泵、冷却塔、管网及末端仿真"""
    def __init__(self, params: HVACParameters):
        self.p = params
        self.rho_water = 1000
        self.cp_water = 4180
        self.rho_air = 1.2
        self.cp_air = 1005

    def calc_chiller(self, Q_load_kW, T_cw_in, dt):
        if Q_load_kW <= 0:
            return 0.0, self.p.chiller_rated_CHW_T_supply, 0.0, 0.0
        PLR = Q_load_kW / self.p.chiller_rated_capacity
        PLR = min(PLR, 1.0)
        PLR = max(PLR, 0.1)
        T_cw_rated = self.p.chiller_rated_CW_T_return
        cop_factor = 1 - 0.025 * (T_cw_in - T_cw_rated)
        plr_factor = (self.p.plr_curve_a + self.p.plr_curve_b * PLR + self.p.plr_curve_c * PLR**2)
        COP = self.p.chiller_rated_COP * cop_factor * plr_factor
        if COP <= 0: COP = 1.0
        chiller_power = Q_load_kW / COP
        T_chw_supply = self.p.chiller_rated_CHW_T_supply
        return chiller_power, T_chw_supply, PLR, COP

    def calc_pump_power(self, flow_rate_m3s, rated_flow, rated_head, rated_eff, motor_eff, is_variable=True):
        if flow_rate_m3s <= 0:
            return 0.0
        rated_flow_m3s = rated_flow / 3600
        flow_ratio = flow_rate_m3s / rated_flow_m3s
        head = rated_head * (0.3 + 0.7 * flow_ratio**2) if is_variable else rated_head
        hydraulic_power = self.rho_water * 9.81 * flow_rate_m3s * head
        pump_eff = rated_eff * (0.5 + 0.5 * flow_ratio)
        pump_eff = min(pump_eff, 0.85)
        shaft_power = hydraulic_power / pump_eff if pump_eff > 0 else 0
        electrical_power = shaft_power / motor_eff
        return electrical_power / 1000

    def calc_cooling_tower(self, heat_rejected_kW, T_wb):
        if heat_rejected_kW <= 0:
            return T_wb, 0.0
        rated_rejection = self.p.chiller_rated_capacity * (1 + 1 / self.p.chiller_rated_COP)
        load_ratio = heat_rejected_kW / rated_rejection
        approach = self.p.ct_rated_approach * (0.5 + 0.5 * load_ratio)
        T_cw_out = T_wb + approach
        fan_power = self.p.ct_rated_fan_power * load_ratio
        return T_cw_out, fan_power

    def calc_pipe_heat_loss(self, T_fluid, T_ambient, inner_dia, length, insul_thick, insul_k):
        r_inner = inner_dia / 2
        r_insul = r_inner + insul_thick
        if r_insul <= r_inner:
            return 0.0
        R_cond = np.log(r_insul / r_inner) / (2 * np.pi * insul_k * length)
        h_out = 10
        R_conv = 1 / (2 * np.pi * r_insul * length * h_out)
        R_total = R_cond + R_conv
        if R_total <= 0:
            return 0.0
        return (T_fluid - T_ambient) / R_total

    def system_simulation(self, Q_sensible_demand, T_room, T_outdoor, T_wb, dt_seconds, T_return_chw_prev=12.0):
        """返回空调系统理论运行状态（未加噪声）"""
        results = {}
        if Q_sensible_demand >= 0:
            results.update({
                'chiller_power_kW': 0.0, 'chw_pump_power_kW': 0.0,
                'cw_pump_power_kW': 0.0, 'ct_fan_power_kW': 0.0,
                'terminal_fan_power_kW': 0.0, 'system_total_power_kW': 0.0,
                'T_chw_supply': 7.0, 'T_chw_return': 12.0,
                'T_cw_supply': 32.0, 'T_cw_return': 37.0,
                'chiller_PLR': 0.0, 'chiller_COP': 0.0,
                'chw_flow_rate': 0.0, 'cw_flow_rate': 0.0,
                'pipe_heat_gain_chw': 0.0
            })
            return results

        Q_load_kW = -Q_sensible_demand / 1000.0
        delta_T_chw = self.p.chiller_rated_CHW_T_return - self.p.chiller_rated_CHW_T_supply
        chw_flow_m3s = Q_load_kW * 1000 / (self.rho_water * self.cp_water * delta_T_chw)
        T_chw_return = T_return_chw_prev

        pipe = self.p.chw_pipe_network
        heat_gain_to_chw = self.calc_pipe_heat_loss(
            self.p.chiller_rated_CHW_T_supply, 28.0,
            pipe["inner_dia_main"], pipe["length_main"],
            pipe["insulation_thickness"], pipe["insulation_conductivity"])
        Q_loss_chw = heat_gain_to_chw / 1000.0
        Q_total_chiller = Q_load_kW + Q_loss_chw

        heat_rejection_est = Q_total_chiller * (1 + 1 / 5.0)
        T_cw_out, ct_fan_power = self.calc_cooling_tower(heat_rejection_est, T_wb)
        chiller_power_true, T_chw_supply, PLR, COP = self.calc_chiller(Q_total_chiller, T_cw_out, dt_seconds)
        heat_rejection = Q_total_chiller + chiller_power_true
        T_cw_out, ct_fan_power = self.calc_cooling_tower(heat_rejection, T_wb)

        cw_flow_m3s = chw_flow_m3s * 1.1
        T_cw_return = T_cw_out + heat_rejection * 1000 / (self.rho_water * self.cp_water * (cw_flow_m3s + 1e-6))

        chw_pump_power = self.calc_pump_power(chw_flow_m3s, self.p.chw_pump_rated_flow,
                                               self.p.chw_pump_rated_head, self.p.chw_pump_rated_efficiency,
                                               self.p.chw_pump_motor_efficiency, True)
        cw_pump_power = self.calc_pump_power(cw_flow_m3s, self.p.cw_pump_rated_flow,
                                             self.p.cw_pump_rated_head, self.p.cw_pump_rated_efficiency,
                                             self.p.cw_pump_motor_efficiency, True)
        num_fcu_on = max(1, int(self.p.fcu_count * PLR))
        terminal_fan_power = num_fcu_on * self.p.fcu_rated_fan_power
        total_power = chiller_power_true + chw_pump_power + cw_pump_power + ct_fan_power + terminal_fan_power

        results.update({
            'chiller_power_kW': chiller_power_true,
            'chw_pump_power_kW': chw_pump_power,
            'cw_pump_power_kW': cw_pump_power,
            'ct_fan_power_kW': ct_fan_power,
            'terminal_fan_power_kW': terminal_fan_power,
            'system_total_power_kW': total_power,
            'T_chw_supply': T_chw_supply,
            'T_chw_return': T_chw_return,
            'T_cw_supply': T_cw_out,
            'T_cw_return': T_cw_return,
            'chiller_PLR': PLR,
            'chiller_COP': COP,
            'chw_flow_rate': chw_flow_m3s,
            'cw_flow_rate': cw_flow_m3s,
            'pipe_heat_gain_chw': Q_loss_chw
        })
        return results

# ==================== 传感器/仪表模拟模块 ====================
def sensor_measurement(true_value, noise_std, bias=0.0, percent=False):
    """模拟传感器测量值：真值 + 偏差 + 高斯噪声"""
    if true_value is None or true_value == 0.0:
        return true_value + bias + np.random.normal(0, noise_std)
    if percent:
        noise = np.random.normal(0, noise_std / 100.0 * abs(true_value))
    else:
        noise = np.random.normal(0, noise_std)
    return true_value + bias + noise

def power_meter(true_power, accuracy_class=0.5):
    """
    模拟冷热源电能表读数
    accuracy_class: 精度等级，如0.5表示最大允许误差±0.5%读数
    """
    if true_power <= 0:
        return 0.0
    error_percent = np.random.uniform(-accuracy_class, accuracy_class)
    measured = true_power * (1 + error_percent / 100.0)
    return max(0.0, measured)

# ==================== 主仿真器 ====================
class Simulator:
    def __init__(self, building: BuildingThermal, hvac: HVACSystem, weather: WeatherData):
        self.building = building
        self.hvac = hvac
        self.weather = weather
        self.dt_minutes = DT_MINUTES
        self.dt_seconds = self.dt_minutes * 60
        self.start_time = SIM_START
        self.end_time = SIM_END
        self.total_steps = int((self.end_time - self.start_time).total_seconds() / 60 / self.dt_minutes) + 1

    def is_weekday(self, dt_obj):
        return dt_obj.weekday() < 5

    def run(self):
        print("生成全年天气数据...")
        T_db, T_wb, solar = generate_annual_weather(self.start_time, self.end_time, self.dt_minutes)

        T_room = 25.0
        T_chw_return_prev = 12.0
        results = {
            'datetime': [],
            'T_outdoor': [], 'T_wb': [], 'solar': [], 'T_room': [],
            'cooling_load_kW': [],
            'chiller_power_kW': [],          # 电能表测量值
            'chw_pump_power_kW': [], 'cw_pump_power_kW': [],
            'ct_fan_power_kW': [], 'terminal_fan_power_kW': [],
            'total_power_kW': [],
            'chiller_PLR': [], 'chiller_COP': [],  # 理论COP
            'chw_flow_rate': [], 'cw_flow_rate': [],
            'T_chw_supply': [], 'T_chw_return': [],
            'T_cw_supply': [], 'T_cw_return': [],
            'pipe_heat_gain_kW': [],
            'chw_flow_sensor': [], 'cw_flow_sensor': [],
            'T_chw_supply_sensor': [], 'T_chw_return_sensor': [],
            'T_cw_supply_sensor': [], 'T_cw_return_sensor': [],
            'chiller_power_true_kW': [],     # 理论冷机功率
            'measured_cooling_kW': [],       # 传感器反算冷量
            'measured_COP': []               # 基于测量值的实际COP
        }

        print(f"开始仿真，总步数: {self.total_steps} ...")
        for step in range(self.total_steps):
            current_dt = self.start_time + timedelta(minutes=step * self.dt_minutes)
            hour_decimal = current_dt.hour + current_dt.minute / 60.0
            weekday = self.is_weekday(current_dt)
            T_out = T_db[step]
            T_wb_val = T_wb[step]
            solar_val = solar[step]

            setpoint = self.hvac.p.cooling_setpoint
            deadband = self.hvac.p.deadband
            if T_room > setpoint + deadband/2:
                internal_sens, _ = self.building.get_internal_gains(hour_decimal, weekday)
                win_area = 1500
                solar_heat = win_area * self.building.env.SHGC * solar_val
                envelope = (T_out - T_room) * self.building.UA_total
                infiltr = self.building.air_density * self.building.air_cp * \
                          (self.building.env.infiltration_ach * self.building.conditioned_volume / 3600) * (T_out - T_room)
                net_gain = internal_sens + solar_heat + envelope + infiltr
                q_supply = -net_gain - self.building.C * (T_room - setpoint) / self.dt_seconds
                q_supply = max(q_supply, -self.hvac.p.chiller_rated_capacity * 1000)
            else:
                q_supply = 0.0

            hvac_result = self.hvac.system_simulation(
                q_supply, T_room, T_out, T_wb_val, self.dt_seconds, T_chw_return_prev)
            T_room = self.building.step(T_room, T_out, solar_val, hour_decimal, weekday, q_supply, self.dt_seconds)

            # ----- 仪表模拟 -----
            p = self.hvac.p
            # 电能表
            true_chiller_power = hvac_result['chiller_power_kW']
            measured_chiller_power = power_meter(true_chiller_power, p.power_meter_accuracy)
            # 更新测量后的总功率
            hvac_result['chiller_power_kW'] = measured_chiller_power
            hvac_result['system_total_power_kW'] = (measured_chiller_power +
                                                    hvac_result['chw_pump_power_kW'] +
                                                    hvac_result['cw_pump_power_kW'] +
                                                    hvac_result['ct_fan_power_kW'] +
                                                    hvac_result['terminal_fan_power_kW'])
            # 流量温度传感器
            chw_flow_sensor = sensor_measurement(hvac_result['chw_flow_rate'],
                                                 p.flow_noise_std, p.sensor_bias, percent=True)
            cw_flow_sensor = sensor_measurement(hvac_result['cw_flow_rate'],
                                                p.flow_noise_std, p.sensor_bias, percent=True)
            T_chw_supply_sensor = sensor_measurement(hvac_result['T_chw_supply'],
                                                     p.temperature_noise_std, p.sensor_bias)
            T_chw_return_sensor = sensor_measurement(hvac_result['T_chw_return'],
                                                     p.temperature_noise_std, p.sensor_bias)
            T_cw_supply_sensor = sensor_measurement(hvac_result['T_cw_supply'],
                                                    p.temperature_noise_std, p.sensor_bias)
            T_cw_return_sensor = sensor_measurement(hvac_result['T_cw_return'],
                                                    p.temperature_noise_std, p.sensor_bias)

            # ----- 基于传感器数据的实际COP计算 -----
            # 冷量 (kW) = 流量(m3/s) * ρ(1000 kg/m3) * cp(4.18 kJ/kg·K) * ΔT(K) / 1000
            # 简化：measured_cooling_kW = chw_flow_sensor * 4180 * (T_return_sensor - T_supply_sensor) / 1000
            delta_T_chw_meas = T_chw_return_sensor - T_chw_supply_sensor
            if chw_flow_sensor > 0 and delta_T_chw_meas > 0:
                measured_cooling = chw_flow_sensor * 4180 * delta_T_chw_meas  # kW
            else:
                measured_cooling = 0.0
            # 实际COP = 冷量 / 电能表功率
            if measured_chiller_power > 0 and measured_cooling > 0:
                measured_COP = measured_cooling / measured_chiller_power
            else:
                measured_COP = 0.0

            # 记录数据
            results['datetime'].append(current_dt)
            results['T_outdoor'].append(T_out)
            results['T_wb'].append(T_wb_val)
            results['solar'].append(solar_val)
            results['T_room'].append(T_room)
            results['cooling_load_kW'].append(-q_supply/1000.0 if q_supply<0 else 0.0)
            results['chiller_power_kW'].append(measured_chiller_power)
            results['chw_pump_power_kW'].append(hvac_result['chw_pump_power_kW'])
            results['cw_pump_power_kW'].append(hvac_result['cw_pump_power_kW'])
            results['ct_fan_power_kW'].append(hvac_result['ct_fan_power_kW'])
            results['terminal_fan_power_kW'].append(hvac_result['terminal_fan_power_kW'])
            results['total_power_kW'].append(hvac_result['system_total_power_kW'])
            results['chiller_PLR'].append(hvac_result['chiller_PLR'])
            results['chiller_COP'].append(hvac_result['chiller_COP'])
            results['chw_flow_rate'].append(hvac_result['chw_flow_rate'])
            results['cw_flow_rate'].append(hvac_result['cw_flow_rate'])
            results['T_chw_supply'].append(hvac_result['T_chw_supply'])
            results['T_chw_return'].append(hvac_result['T_chw_return'])
            results['T_cw_supply'].append(hvac_result['T_cw_supply'])
            results['T_cw_return'].append(hvac_result['T_cw_return'])
            results['pipe_heat_gain_kW'].append(hvac_result['pipe_heat_gain_chw'])
            results['chw_flow_sensor'].append(chw_flow_sensor)
            results['cw_flow_sensor'].append(cw_flow_sensor)
            results['T_chw_supply_sensor'].append(T_chw_supply_sensor)
            results['T_chw_return_sensor'].append(T_chw_return_sensor)
            results['T_cw_supply_sensor'].append(T_cw_supply_sensor)
            results['T_cw_return_sensor'].append(T_cw_return_sensor)
            results['chiller_power_true_kW'].append(true_chiller_power)
            results['measured_cooling_kW'].append(measured_cooling)
            results['measured_COP'].append(measured_COP)

            T_chw_return_prev = hvac_result['T_chw_return']

            if step % PRINT_INTERVAL == 0:
                print(f"进度: {step}/{self.total_steps} ({current_dt})")

        df = pd.DataFrame(results)
        print("仿真完成！")
        return df

# ==================== 主程序入口 ====================
if __name__ == "__main__":
    env = BuildingEnvelope()
    internal = InternalLoad()
    hvac_param = HVACParameters()
    weather = WeatherData()

    building = BuildingThermal(env, internal)
    hvac = HVACSystem(hvac_param)

    # 演示用一周数据（7月1日~7日）
    SIM_START = datetime(2024, 7, 1, 0, 0)
    SIM_END   = datetime(2024, 7, 7, 23, 59)
    PRINT_INTERVAL = 60 * 24

    sim = Simulator(building, hvac, weather)
    sim.start_time = SIM_START
    sim.end_time = SIM_END
    sim.total_steps = int((SIM_END - SIM_START).total_seconds() / 60) + 1

    df = sim.run()

    # 保存结果
    df.to_csv("hvac_simulation_full.csv", index=False, encoding='utf-8-sig')
    print("结果已保存至 hvac_simulation_full.csv")

    # ---------- 绘制中文图表 ----------
    plt.figure(figsize=(14, 14))
    plt.subplot(5, 1, 1)
    plt.plot(df['datetime'], df['T_room'], label='Room Temperature')
    plt.plot(df['datetime'], df['T_outdoor'], label='Outdoor Temperature', alpha=0.6)
    plt.legend()
    plt.ylabel('Temperature (℃)')
    plt.title('Building Thermal Environment & System Performance (with Instrument Measurements)')

    plt.subplot(5, 1, 2)
    plt.plot(df['datetime'], df['cooling_load_kW'], label='Cooling Load')
    plt.plot(df['datetime'], df['total_power_kW'], label='System Total Power (metered)')
    plt.legend()
    plt.ylabel('Power (kW)')

    plt.subplot(5, 1, 3)
    plt.plot(df['datetime'], df['chiller_power_true_kW'], '--', label='Chiller Power (theoretical)', alpha=0.7)
    plt.plot(df['datetime'], df['chiller_power_kW'], label='Chiller Power (meter)', color='red')
    plt.legend()
    plt.ylabel('Chiller Power (kW)')

    plt.subplot(5, 1, 4)
    plt.plot(df['datetime'], df['chiller_COP'], label='COP (theoretical)', alpha=0.7)
    plt.plot(df['datetime'], df['measured_COP'], label='COP (measured)', color='green')
    plt.legend()
    plt.ylabel('COP')
    plt.ylim(0, max(df['chiller_COP'].max(), df['measured_COP'].max())*1.1)

    plt.subplot(5, 1, 5)
    plt.plot(df['datetime'], df['T_chw_supply_sensor'], label='CHW Supply (sensor)', color='blue')
    plt.plot(df['datetime'], df['T_chw_return_sensor'], label='CHW Return (sensor)', color='cyan')
    plt.plot(df['datetime'], df['T_cw_supply_sensor'], label='CW Supply (sensor)', color='red')
    plt.plot(df['datetime'], df['T_cw_return_sensor'], label='CW Return (sensor)', color='orange')
    plt.legend()
    plt.ylabel('Water Temperature (℃)')
    plt.xlabel('Time')

    plt.tight_layout()
    plt.savefig('simulation_full_plot.png')
    plt.show()