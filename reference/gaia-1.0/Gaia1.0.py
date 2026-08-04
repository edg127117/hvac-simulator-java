# ==================== 办公建筑中央空调系统全年逐分钟仿真程序 ====================
# 功能：生成建筑热过程、管网水力热力、冷热源能耗的全方位运行数据
# 步长可配置，默认1分钟；输出时间序列CSV及可视化图表

import numpy as np
import pandas as pd
from dataclasses import dataclass, field
from typing import List, Tuple, Optional
import matplotlib.pyplot as plt
from datetime import datetime, timedelta
import warnings
warnings.filterwarnings('ignore')

# ==================== 全局仿真参数 ====================
# 仿真起止时间（可根据需要修改）
SIM_START = datetime(2024, 1, 1, 0, 0)          # 仿真开始时间
SIM_END   = datetime(2024, 12, 31, 23, 59)      # 仿真结束时间（全年）
DT_MINUTES = 1                                   # 仿真时间步长（分钟），最小颗粒度1分钟
PRINT_INTERVAL = 60 * 24 * 30                    # 控制台进度打印间隔（分钟），默认每30天打印一次

# ==================== 参数数据类 ====================

@dataclass
class WeatherData:
    """气象参数（使用合成数据模拟全年变化）"""
    lat: float = 30.5            # 地理纬度（°）
    lon: float = 114.3           # 地理经度（°）
    T_db_design: float = 35.0    # 夏季空调室外计算干球温度（℃）
    T_wb_design: float = 28.0    # 夏季空调室外计算湿球温度（℃）
    # 以下数组在仿真时动态生成
    T_db: np.ndarray = None      # 逐时干球温度序列（℃）
    T_wb: np.ndarray = None      # 逐时湿球温度序列（℃）
    solar_global: np.ndarray = None  # 水平面太阳总辐射序列（W/m²）

@dataclass
class BuildingEnvelope:
    """建筑围护结构及几何参数"""
    total_area: float = 12000          # 总建筑面积（m²）
    conditioned_area: float = 10000    # 空调面积（m²）
    num_floors: int = 10               # 地上层数
    floor_height: float = 3.6          # 标准层层高（m）
    volume_per_floor: float = 1200 * 3.6  # 每层体积（m³），示例值
    # 窗墙比（各朝向）
    window_wall_ratio_s: float = 0.5   # 南向窗墙比
    window_wall_ratio_n: float = 0.3   # 北向窗墙比
    window_wall_ratio_e: float = 0.4   # 东向窗墙比
    window_wall_ratio_w: float = 0.4   # 西向窗墙比
    # 围护结构传热系数 W/(m²·K)
    U_wall: float = 0.45               # 外墙传热系数
    U_roof: float = 0.35               # 屋顶传热系数
    U_window: float = 2.0              # 外窗传热系数
    SHGC: float = 0.35                 # 外窗太阳得热系数（太阳能总透射比）
    # RC模型热惰性参数（等效热容和热阻）
    C_wall: float = 5000000            # 建筑综合热容（J/K），考虑墙体、家具等蓄热
    R_wall: float = 0.02               # 等效热阻（K/W），来自1/(U*A)的综合近似
    # 气密性参数
    infiltration_ach: float = 0.2      # 渗透换气次数（次/h）

@dataclass
class InternalLoad:
    """内部负荷及使用时间表"""
    occ_density: float = 0.1            # 人员密度（人/m²）
    sensible_per_person: float = 65     # 人员显热散热量（W/人）
    latent_per_person: float = 55       # 人员潜热散热量（W/人）
    lighting_power_density: float = 9.0 # 照明功率密度（W/m²）
    equipment_power_density: float = 15.0  # 设备功率密度（W/m²）
    outdoor_air_rate: float = 30        # 人均新风量标准（m³/(h·人)）
    # 时间表（0~1系数）将在初始化时生成
    schedule_weekday: np.ndarray = None # 工作日24小时系数
    schedule_weekend: np.ndarray = None # 周末24小时系数

@dataclass
class HVACParameters:
    """中央空调系统详细技术参数"""
    # ---------- 冷热源 ----------
    chiller_type: str = "离心式冷水机组"
    chiller_rated_capacity: float = 1400  # 额定制冷量（kW）
    chiller_rated_COP: float = 6.0        # 额定性能系数
    chiller_rated_CHW_T_supply: float = 7   # 冷冻水额定供水温度（℃）
    chiller_rated_CHW_T_return: float = 12  # 冷冻水额定回水温度（℃）
    chiller_rated_CW_T_supply: float = 32   # 冷却水额定供水温度（进冷却塔）（℃）
    chiller_rated_CW_T_return: float = 37   # 冷却水额定回水温度（进冷机）（℃）
    # 部分负荷特性系数（COP/COP_rated = a + b*PLR + c*PLR²）
    plr_curve_a: float = 0.2
    plr_curve_b: float = 0.8
    plr_curve_c: float = 0.0

    # ---------- 冷冻水管网 ----------
    chw_pipe_network: dict = field(default_factory=lambda: {
        "topology": "支状同程式",              # 管网拓扑
        "DN_main": 200,                       # 主管公称直径（mm）
        "inner_dia_main": 0.207,              # 主管内径（m）
        "length_main": 200,                   # 主管总长度（m）
        "abs_roughness": 0.000046,            # 管壁绝对粗糙度（m），钢管
        "insulation_thickness": 0.03,         # 保温层厚度（m）
        "insulation_conductivity": 0.034,     # 保温材料导热系数（W/(m·K)）
        "pipe_material": "碳钢无缝钢管"        # 管壁材质
    })

    # ---------- 冷却水管网 ----------
    cw_pipe_network: dict = field(default_factory=lambda: {
        "DN_main": 250,
        "inner_dia_main": 0.257,
        "length_main": 200,
        "insulation_thickness": 0.03,
        "insulation_conductivity": 0.034,
    })

    # ---------- 冷冻水泵 ----------
    chw_pump_rated_flow: float = 200         # 额定流量（m³/h）
    chw_pump_rated_head: float = 28          # 额定扬程（m）
    chw_pump_rated_efficiency: float = 0.75  # 额定水泵效率
    chw_pump_motor_efficiency: float = 0.9   # 电机效率
    chw_pump_control: str = "最不利末端恒压差控制(变频)"  # 控制方式

    # ---------- 冷却水泵 ----------
    cw_pump_rated_flow: float = 220
    cw_pump_rated_head: float = 25
    cw_pump_rated_efficiency: float = 0.78
    cw_pump_motor_efficiency: float = 0.9

    # ---------- 冷却塔 ----------
    ct_rated_flow: float = 220               # 额定冷却水流量（m³/h）
    ct_rated_fan_power: float = 7.5          # 额定风机功率（kW）
    ct_rated_approach: float = 4.0            # 额定逼近度（℃），即出水温度与湿球温度之差

    # ---------- 空调末端（风机盘管FCU）----------
    terminal_type: str = "FCU"
    fcu_count: int = 200                     # FCU总台数
    fcu_rated_air_flow: float = 0.15         # 单台额定风量（m³/s）
    fcu_rated_fan_power: float = 0.05        # 单台风机功率（kW）
    fcu_rated_water_flow: float = 0.2        # 单台额定水流量（L/s），此处单位兼容，后续计算转m³/s
    fcu_rated_pressure_drop: float = 30      # 单台水侧额定压降（kPa）

    # ---------- 控制参数 ----------
    cooling_setpoint: float = 25.0           # 夏季室温设定值（℃）
    heating_setpoint: float = 20.0           # 冬季室温设定值（℃）
    deadband: float = 2.0                    # 控制死区（℃）
    chw_reset_schedule: str = "固定7℃"       # 冷冻水供水温度重置策略

# ==================== 气象数据生成器 ====================
def generate_annual_weather(start, end, dt_minutes):
    """
    生成全年逐分钟的合成气象数据（干球温度、湿球温度、太阳总辐射）
    输入：起止时间，步长（分钟）
    返回：温度与辐射的numpy数组
    """
    # 计算总时间步数
    total_minutes = int((end - start).total_seconds() / 60) + 1
    t = np.arange(total_minutes, dtype=float)  # 步序号
    # 将分钟数转换为一年中的天数（0~365.25）
    days_of_year = (t * dt_minutes) / (24 * 60)

    # ---------- 干球温度：年周期 + 日周期 + 随机扰动 ----------
    # 年周期：均值18℃，振幅12℃，最低点出现在1月中旬（约第15天）
    T_annual = 18 - 12 * np.cos(2 * np.pi * (days_of_year - 15) / 365)
    # 日周期：振幅5℃，最高温出现在14时
    hours = (t * dt_minutes / 60) % 24
    T_diurnal = 5 * np.sin(2 * np.pi * (hours - 14) / 24)
    T_db = T_annual + T_diurnal + 0.8 * np.random.randn(len(hours))

    # ---------- 湿球温度：与干球关联，夏季湿球较高，冬季较低 ----------
    # 湿球低于干球，夏季差约5~8℃，冬季差2~4℃，用正弦函数模拟季节湿差变化
    season_factor = (np.sin(2 * np.pi * (days_of_year - 170) / 365) * 0.5 + 0.5)  # 夏季为1，冬季为0
    wet_depression = 3 + 5 * season_factor   # 干湿球温差
    T_wb = T_db - wet_depression + 0.5 * np.random.randn(len(hours))

    # ---------- 太阳总辐射：基于太阳高度角和大气透明度 ----------
    # 太阳赤纬（简化公式）
    declination = 23.45 * np.sin(2 * np.pi * (284 + days_of_year) / 365)
    # 时角（度），正午12时为0
    hour_angle = (hours - 12) * 15
    # 纬度（弧度）
    lat_rad = np.radians(30.5)
    dec_rad = np.radians(declination)
    ha_rad = np.radians(hour_angle)
    # 太阳高度角正弦值
    sin_altitude = np.sin(lat_rad) * np.sin(dec_rad) + np.cos(lat_rad) * np.cos(dec_rad) * np.cos(ha_rad)
    altitude = np.arcsin(np.clip(sin_altitude, 0, 1))  # 高度角（弧度）
    # 太阳常数 1367 W/m²，大气透明度取0.7的修正
    solar_constant = 1367
    air_mass = 1 / np.clip(sin_altitude, 0.01, 1)       # 大气质量近似
    I_direct = solar_constant * np.power(0.7, air_mass) * sin_altitude
    I_diffuse = 0.3 * solar_constant * sin_altitude      # 散射辐射（经验比例）
    I_global = I_direct + I_diffuse
    I_global[altitude <= 0] = 0                         # 夜间无辐射
    # 随机云量扰动（0.6~1.2倍）
    I_global *= np.random.uniform(0.6, 1.2, len(hours))
    I_global = np.clip(I_global, 0, None)               # 保证非负

    return T_db, T_wb, I_global

# ==================== 建筑热平衡模型（RC网络） ====================
class BuildingThermal:
    """建筑动态热模型，采用一阶RC模型（集总热容法）"""
    def __init__(self, env: BuildingEnvelope, internal: InternalLoad):
        self.env = env
        self.internal = internal
        # 计算综合传热系数 UA（W/K）：将各围护结构面积与传热系数相乘后求和
        # 外墙面积、窗面积、屋顶面积采用经验估算值（实际可从建筑数据计算）
        wall_area = 3000      # 外墙总面积（m²）
        win_area = 1500       # 外窗总面积（m²）
        roof_area = 1200      # 屋顶面积（m²）
        self.UA_total = env.U_wall * wall_area + env.U_roof * roof_area + env.U_window * win_area
        # 等效热阻 R_equiv = 1/UA（K/W）
        self.R_equiv = 1 / self.UA_total if self.UA_total != 0 else 0.1
        # 建筑综合热容（J/K）
        self.C = env.C_wall
        # 建筑空调区总体积（用于新风、渗透计算）
        self.conditioned_volume = env.conditioned_area * env.floor_height * env.num_floors
        # 空气物性常数
        self.air_density = 1.2         # kg/m³
        self.air_cp = 1005             # J/(kg·K)
        self.latent_heat_vap = 2450e3  # 水蒸气汽化潜热（J/kg），若需潜热计算
        # 生成内部负荷时间表（24小时，逐时系数）
        hours = np.arange(24)
        # 工作日：8-18点为1.0，其余为0
        weekday = np.zeros(24)
        weekday[8:18] = 1.0
        # 周末：10-16点为0.5，其余为0
        weekend = np.zeros(24)
        weekend[10:16] = 0.5
        self.schedule_weekday = weekday
        self.schedule_weekend = weekend

    def get_internal_gains(self, timestep_hour, is_weekday):
        """
        根据时间与工作日标志返回当前内部得热（显热和潜热）
        返回：显热得热（W），潜热得热（W）
        """
        hour = int(timestep_hour % 24)  # 一天中的小时数
        coeff = self.schedule_weekday[hour] if is_weekday else self.schedule_weekend[hour]
        area = self.env.conditioned_area
        # 人员数量
        occ = area * self.internal.occ_density
        # 人员显热、潜热
        sensible_occ = occ * self.internal.sensible_per_person * coeff
        latent_occ = occ * self.internal.latent_per_person * coeff
        # 照明与设备
        light = area * self.internal.lighting_power_density * coeff
        equip = area * self.internal.equipment_power_density * coeff
        # 显热总得热（照明、设备全为显热）
        return sensible_occ + light + equip, latent_occ

    def get_outdoor_air_load(self, T_indoor, T_outdoor, W_indoor, W_outdoor, is_weekday, hour):
        """
        计算新风负荷（显热+潜热）
        注：本简化仿真未详细计算室内外含湿量，此方法预留
        """
        # 当前时刻人员数量决定新风量
        coeff = self.schedule_weekday[int(hour % 24)] if is_weekday else self.schedule_weekend[int(hour % 24)]
        people = self.env.conditioned_area * self.internal.occ_density * coeff
        outdoor_air_flow = people * self.internal.outdoor_air_rate / 3600  # m³/s
        # 显热负荷
        sensible = self.air_density * self.air_cp * outdoor_air_flow * (T_outdoor - T_indoor)
        # 潜热负荷（含湿量差单位g/kg，乘以1e-3转换）
        latent = self.air_density * self.latent_heat_vap * outdoor_air_flow * (W_outdoor - W_indoor) * 1e-3
        return sensible, latent

    def step(self, T_indoor, T_outdoor, solar_gain, timestep_hour, is_weekday,
             q_sensible_supply, dt_seconds):
        """
        单步更新室内温度（显热平衡）
        T_indoor: 当前室温（℃）
        T_outdoor: 室外温度（℃）
        solar_gain: 太阳辐射强度（W/m²）
        timestep_hour: 当前时刻的小数小时
        is_weekday: 是否工作日
        q_sensible_supply: 空调系统提供的显热冷量（W），供冷时为负值
        dt_seconds: 时间步长（秒）
        返回：新室温（℃）
        """
        # 1. 内部得热（显热部分）
        internal_sensible, _ = self.get_internal_gains(timestep_hour, is_weekday)
        # 2. 太阳辐射得热（窗面积 × SHGC × 太阳辐射）
        win_area = 1500  # 窗面积 m²（与初始化一致）
        solar_heat = win_area * self.env.SHGC * solar_gain
        # 3. 通过围护结构的传热（室外→室内为正）
        envelope_heat = (T_outdoor - T_indoor) * self.UA_total
        # 4. 空气渗透显热（忽略渗透潜热）
        infiltration_flow = self.env.infiltration_ach * self.conditioned_volume / 3600  # m³/s
        infiltration_sensible = self.air_density * self.air_cp * infiltration_flow * (T_outdoor - T_indoor)
        # 5. 净进入室内的热量（未考虑空调） = 各项得热之和
        net_heat = internal_sensible + solar_heat + envelope_heat + infiltration_sensible + q_sensible_supply
        # 6. 温度更新（欧拉法）：ΔT = (净热量 × 时间步长) / 热容
        dT = net_heat * dt_seconds / self.C
        T_new = T_indoor + dT
        return T_new

# ==================== 中央空调系统模型 ====================
class HVACSystem:
    """包含冷热源、水泵、冷却塔、末端及管网热损失的仿真模型"""
    def __init__(self, params: HVACParameters):
        self.p = params
        # 水的物性
        self.rho_water = 1000    # kg/m³
        self.cp_water = 4180     # J/(kg·K)
        self.rho_air = 1.2
        self.cp_air = 1005

    def calc_chiller(self, Q_load_kW, T_cw_in, dt):
        """
        计算冷机电耗及运行状态
        Q_load_kW: 冷负荷需求（kW）
        T_cw_in: 冷却水进水温度（℃）
        返回：冷机电功率(kW), 冷冻水供水温度(℃), 部分负荷率PLR, 实际COP
        """
        if Q_load_kW <= 0:
            return 0.0, self.p.chiller_rated_CHW_T_supply, 0.0, 0.0
        # 部分负荷率 (PLR)
        PLR = Q_load_kW / self.p.chiller_rated_capacity
        PLR = min(PLR, 1.0)            # 不超过1
        PLR = max(PLR, 0.1)            # 最低部分负荷（避免除零）
        # COP修正：冷却水温度偏离额定值的影响（每度降低2.5%）
        T_cw_rated = self.p.chiller_rated_CW_T_return  # 额定37℃
        cop_factor = 1 - 0.025 * (T_cw_in - T_cw_rated)
        # 部分负荷修正系数
        plr_factor = (self.p.plr_curve_a + self.p.plr_curve_b * PLR + self.p.plr_curve_c * PLR**2)
        # 实际COP
        COP = self.p.chiller_rated_COP * cop_factor * plr_factor
        if COP <= 0:
            COP = 1.0
        # 冷机电功率 = 制冷量 / COP
        chiller_power = Q_load_kW / COP   # kW
        # 冷冻水供水温度（当前策略固定为额定值）
        T_chw_supply = self.p.chiller_rated_CHW_T_supply
        return chiller_power, T_chw_supply, PLR, COP

    def calc_pump_power(self, flow_rate_m3s, rated_flow, rated_head, rated_eff, motor_eff, is_variable=True):
        """
        计算水泵电功率（支持变频）
        flow_rate_m3s: 实际流量（m³/s）
        返回：电功率（kW）
        """
        if flow_rate_m3s <= 0:
            return 0.0
        # 额定流量转换为 m³/s
        rated_flow_m3s = rated_flow / 3600
        flow_ratio = flow_rate_m3s / rated_flow_m3s
        # 扬程计算：恒压差控制简化模型（部分扬程随流量平方变化）
        if is_variable:
            head = rated_head * (0.3 + 0.7 * flow_ratio**2)
        else:
            head = rated_head
        # 水功率 (W)
        hydraulic_power = self.rho_water * 9.81 * flow_rate_m3s * head
        # 水泵效率（随流量比近似线性变化，上限0.85）
        pump_eff = rated_eff * (0.5 + 0.5 * flow_ratio)
        pump_eff = min(pump_eff, 0.85)
        # 轴功率
        shaft_power = hydraulic_power / pump_eff if pump_eff > 0 else 0
        # 电动机输入电功率
        electrical_power = shaft_power / motor_eff
        return electrical_power / 1000   # 转换为 kW

    def calc_cooling_tower(self, heat_rejected_kW, T_wb):
        """
        计算冷却塔出口水温及风机功率
        heat_rejected_kW: 需要散发的热量（kW）
        T_wb: 室外湿球温度（℃）
        返回：冷却水出水温度（进冷机）（℃），冷却塔风机功率（kW）
        """
        if heat_rejected_kW <= 0:
            return T_wb, 0.0
        # 额定散热量 = 额定制冷量 + 额定冷机功率
        rated_rejection = self.p.chiller_rated_capacity * (1 + 1 / self.p.chiller_rated_COP)
        load_ratio = heat_rejected_kW / rated_rejection
        # 逼近度（随负荷率变化）
        approach = self.p.ct_rated_approach * (0.5 + 0.5 * load_ratio)
        T_cw_out = T_wb + approach
        # 风机功率（按负荷率线性）
        fan_power = self.p.ct_rated_fan_power * load_ratio
        return T_cw_out, fan_power

    def calc_pipe_heat_loss(self, T_fluid, T_ambient, inner_dia, length, insul_thick, insul_k):
        """
        计算管道沿途热损失（或冷量损失）
        返回：热损失（W），正值表示流体向环境散热（冷量损失），负值表示吸热
        """
        r_inner = inner_dia / 2
        r_insul = r_inner + insul_thick
        if r_insul <= r_inner:
            return 0.0
        # 保温层导热热阻
        R_cond = np.log(r_insul / r_inner) / (2 * np.pi * insul_k * length)
        # 外表面自然对流热阻（取等效换热系数10 W/m²K）
        h_out = 10
        R_conv = 1 / (2 * np.pi * r_insul * length * h_out)
        R_total = R_cond + R_conv
        if R_total <= 0:
            return 0.0
        heat_loss = (T_fluid - T_ambient) / R_total   # W
        return heat_loss

    def system_simulation(self, Q_sensible_demand, T_room, T_outdoor, T_wb, dt_seconds, T_return_chw_prev=12.0):
        """
        整个空调系统仿真接口
        Q_sensible_demand: 房间所需的显热冷量（W），负值代表需要供冷
        返回：一个包含各设备功率、温度、流量等参数的字典
        """
        results = {}
        # 若无供冷需求，所有设备停机
        if Q_sensible_demand >= 0:
            results['chiller_power_kW'] = 0.0
            results['chw_pump_power_kW'] = 0.0
            results['cw_pump_power_kW'] = 0.0
            results['ct_fan_power_kW'] = 0.0
            results['terminal_fan_power_kW'] = 0.0
            results['system_total_power_kW'] = 0.0
            results['T_chw_supply'] = 7.0
            results['T_chw_return'] = 12.0
            results['T_cw_supply'] = 32.0
            results['chiller_PLR'] = 0.0
            results['chiller_COP'] = 0.0
            results['chw_flow_rate'] = 0.0
            results['pipe_heat_gain_chw'] = 0.0
            return results

        # 需要供冷，将需求转换为正值的冷负荷 (kW)
        Q_load_kW = -Q_sensible_demand / 1000.0

        # 1. 根据设计温差（5℃）计算所需冷冻水流量
        delta_T_chw = self.p.chiller_rated_CHW_T_return - self.p.chiller_rated_CHW_T_supply  # 5 K
        # 流量 m³/s  Q(kW) = m_dot * cp * deltaT  => m_dot = Q / (cp*deltaT)
        chw_flow_m3s = Q_load_kW * 1000 / (self.rho_water * self.cp_water * delta_T_chw)
        # 上一时刻回水温度（用于初算，简化处理）
        T_chw_return = T_return_chw_prev

        # 2. 计算冷冻水管网冷量损失（供水管路向环境吸热）
        pipe = self.p.chw_pipe_network
        T_amb_pipe = 28.0  # 管井环境温度（℃），可调整
        heat_gain_to_chw = self.calc_pipe_heat_loss(
            self.p.chiller_rated_CHW_T_supply, T_amb_pipe,
            pipe["inner_dia_main"], pipe["length_main"],
            pipe["insulation_thickness"], pipe["insulation_conductivity"]
        )  # 正值表示外界向管内传热，造成冷量损失
        Q_loss_chw = heat_gain_to_chw / 1000.0   # kW
        # 冷机实际需承担的制冷量 = 房间负荷 + 管网冷量损失
        Q_total_chiller = Q_load_kW + Q_loss_chw

        # 3. 冷却侧初步估算（用于冷机模型）
        # 先假设 COP≈5 估算散热量，计算冷却水温度
        heat_rejection_est = Q_total_chiller * (1 + 1 / 5.0)
        T_cw_out, ct_fan_power = self.calc_cooling_tower(heat_rejection_est, T_wb)

        # 4. 详细冷机计算（使用冷却水出水温度）
        chiller_power, T_chw_supply, PLR, COP = self.calc_chiller(Q_total_chiller, T_cw_out, dt_seconds)
        # 重新计算实际散热量
        heat_rejection = Q_total_chiller + chiller_power
        T_cw_out, ct_fan_power = self.calc_cooling_tower(heat_rejection, T_wb)

        # 5. 冷冻水泵功率
        chw_pump_power = self.calc_pump_power(
            chw_flow_m3s,
            self.p.chw_pump_rated_flow,
            self.p.chw_pump_rated_head,
            self.p.chw_pump_rated_efficiency,
            self.p.chw_pump_motor_efficiency,
            is_variable=True
        )
        # 6. 冷却水泵流量（简化：按冷冻水流量的1.1倍）
        cw_flow_m3s = chw_flow_m3s * 1.1
        cw_pump_power = self.calc_pump_power(
            cw_flow_m3s,
            self.p.cw_pump_rated_flow,
            self.p.cw_pump_rated_head,
            self.p.cw_pump_rated_efficiency,
            self.p.cw_pump_motor_efficiency,
            is_variable=True
        )

        # 7. 末端风机功率（根据部分负荷率开启相应数量FCU）
        num_fcu_on = max(1, int(self.p.fcu_count * PLR))  # 至少开启1台
        terminal_fan_power = num_fcu_on * self.p.fcu_rated_fan_power

        # 8. 系统总电功率
        total_power = chiller_power + chw_pump_power + cw_pump_power + ct_fan_power + terminal_fan_power

        # 整理输出
        results['chiller_power_kW'] = chiller_power
        results['chw_pump_power_kW'] = chw_pump_power
        results['cw_pump_power_kW'] = cw_pump_power
        results['ct_fan_power_kW'] = ct_fan_power
        results['terminal_fan_power_kW'] = terminal_fan_power
        results['system_total_power_kW'] = total_power
        results['T_chw_supply'] = T_chw_supply
        results['T_chw_return'] = T_chw_return      # 此处直接沿用上一时刻回水，可进一步迭代
        results['T_cw_supply'] = T_cw_out            # 进冷机的冷却水温度
        results['chiller_PLR'] = PLR
        results['chiller_COP'] = COP
        results['chw_flow_rate'] = chw_flow_m3s
        results['pipe_heat_gain_chw'] = Q_loss_chw
        # 供水管段温升（℃）
        results['delta_T_pipe'] = heat_gain_to_chw / (self.rho_water * self.cp_water * chw_flow_m3s + 1e-6)
        return results

# ==================== 主仿真器 ====================
class Simulator:
    """组织建筑、空调系统与气象数据的逐时仿真"""
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
        """判断给定日期是否为工作日（周一至周五）"""
        return dt_obj.weekday() < 5

    def run(self):
        """执行仿真主循环，返回包含所有时间步结果的DataFrame"""
        # 生成全年天气序列
        print("生成全年天气数据...")
        T_db, T_wb, solar = generate_annual_weather(self.start_time, self.end_time, self.dt_minutes)

        # 初始化状态变量
        T_room = 25.0               # 初始室温（℃）
        T_chw_return_prev = 12.0    # 上一时刻冷冻水回水温度（℃）
        # 存储结果的字典
        results = {
            'datetime': [],
            'T_outdoor': [], 'T_wb': [], 'solar': [],
            'T_room': [],
            'cooling_load_kW': [],
            'chiller_power_kW': [],
            'chw_pump_power_kW': [],
            'cw_pump_power_kW': [],
            'ct_fan_power_kW': [],
            'terminal_fan_power_kW': [],
            'total_power_kW': [],
            'chiller_PLR': [],
            'chiller_COP': [],
            'T_chw_supply': [],
            'T_cw_supply': [],
            'pipe_heat_gain_kW': []
        }

        print(f"开始仿真，总步数: {self.total_steps} ...")
        # 主循环
        for step in range(self.total_steps):
            # 当前仿真时间
            current_dt = self.start_time + timedelta(minutes=step * self.dt_minutes)
            hour_decimal = current_dt.hour + current_dt.minute / 60.0
            weekday = self.is_weekday(current_dt)

            T_out = T_db[step]
            T_wb_val = T_wb[step]
            solar_val = solar[step]

            # ---------- 控制逻辑：决定空调供冷量 ----------
            setpoint = self.hvac.p.cooling_setpoint
            deadband = self.hvac.p.deadband
            # 当室温高于设定值+死区一半时启动供冷
            if T_room > setpoint + deadband/2:
                # 计算当前时刻不含空调的房间净得热（用于前馈）
                internal_sens, _ = self.building.get_internal_gains(hour_decimal, weekday)
                win_area = 1500
                solar_heat = win_area * self.building.env.SHGC * solar_val
                envelope = (T_out - T_room) * self.building.UA_total
                infiltr = self.building.air_density * self.building.air_cp * \
                          (self.building.env.infiltration_ach * self.building.conditioned_volume / 3600) * (T_out - T_room)
                net_gain = internal_sens + solar_heat + envelope + infiltr
                # 所需供冷量（负值）：既要抵消当前得热，又要使室温趋向设定点
                # q_supply = -net_gain - C*(T_room - setpoint)/dt
                q_supply = -net_gain - self.building.C * (T_room - setpoint) / self.dt_seconds
                # 限制最大制冷量不超过冷机额定值
                q_supply = max(q_supply, -self.hvac.p.chiller_rated_capacity * 1000)
            else:
                q_supply = 0.0   # 不供冷

            # 空调系统仿真，得到各设备能耗与状态
            hvac_result = self.hvac.system_simulation(
                q_supply, T_room, T_out, T_wb_val, self.dt_seconds, T_chw_return_prev
            )

            # 更新室温（使用实际供冷量，此处直接采用需求q_supply，假设空调完全响应）
            T_room = self.building.step(
                T_room, T_out, solar_val, hour_decimal, weekday, q_supply, self.dt_seconds
            )

            # 记录本步结果
            results['datetime'].append(current_dt)
            results['T_outdoor'].append(T_out)
            results['T_wb'].append(T_wb_val)
            results['solar'].append(solar_val)
            results['T_room'].append(T_room)
            results['cooling_load_kW'].append(-q_supply/1000.0 if q_supply < 0 else 0.0)
            results['chiller_power_kW'].append(hvac_result['chiller_power_kW'])
            results['chw_pump_power_kW'].append(hvac_result['chw_pump_power_kW'])
            results['cw_pump_power_kW'].append(hvac_result['cw_pump_power_kW'])
            results['ct_fan_power_kW'].append(hvac_result['ct_fan_power_kW'])
            results['terminal_fan_power_kW'].append(hvac_result['terminal_fan_power_kW'])
            results['total_power_kW'].append(hvac_result['system_total_power_kW'])
            results['chiller_PLR'].append(hvac_result['chiller_PLR'])
            results['chiller_COP'].append(hvac_result['chiller_COP'])
            results['T_chw_supply'].append(hvac_result['T_chw_supply'])
            results['T_cw_supply'].append(hvac_result['T_cw_supply'])
            results['pipe_heat_gain_kW'].append(hvac_result['pipe_heat_gain_chw'])

            # 更新回水温度（用于下一时刻管网热损失估算）
            T_chw_return_prev = hvac_result['T_chw_return']

            # 定期打印进度
            if step % PRINT_INTERVAL == 0:
                print(f"进度: {step}/{self.total_steps} ({current_dt})")

        df = pd.DataFrame(results)
        print("仿真完成！")
        return df

# ==================== 主程序入口 ====================
if __name__ == "__main__":
    # 实例化所有参数类
    env = BuildingEnvelope()
    internal = InternalLoad()
    hvac_param = HVACParameters()
    weather = WeatherData()

    # 创建建筑、空调系统对象
    building = BuildingThermal(env, internal)
    hvac = HVACSystem(hvac_param)

    # ---------- 为快速演示，仿真时间调整为一周（7月1日~7日） ----------
    # 直接修改模块级变量（处于模块顶层，无需 global）
    SIM_START = datetime(2024, 7, 1, 0, 0)
    SIM_END   = datetime(2024, 7, 7, 23, 59)
    PRINT_INTERVAL = 60 * 24   # 每天打印一次进度

    sim = Simulator(building, hvac, weather)
    # 更新Simulator中的时间范围（因为已修改全局变量）
    sim.start_time = SIM_START
    sim.end_time = SIM_END
    sim.total_steps = int((SIM_END - SIM_START).total_seconds() / 60) + 1

    # 运行仿真
    df = sim.run()

    # 保存结果为CSV
    df.to_csv("hvac_simulation_results.csv", index=False)
    print("结果已保存至 hvac_simulation_results.csv")

    # ---------- 简单可视化 ----------
    plt.figure(figsize=(12, 10))
    plt.subplot(3, 1, 1)
    plt.plot(df['datetime'], df['T_room'], label='室温')
    plt.plot(df['datetime'], df['T_outdoor'], label='室外温度', alpha=0.7)
    plt.legend()
    plt.ylabel('温度 (℃)')

    plt.subplot(3, 1, 2)
    plt.plot(df['datetime'], df['cooling_load_kW'], label='冷负荷')
    plt.plot(df['datetime'], df['total_power_kW'], label='系统总功率')
    plt.legend()
    plt.ylabel('功率 (kW)')

    plt.subplot(3, 1, 3)
    plt.plot(df['datetime'], df['chiller_COP'], label='COP')
    plt.legend()
    plt.ylabel('COP')
    plt.xlabel('时间')
    plt.tight_layout()
    plt.savefig('simulation_plot.png')
    plt.show()