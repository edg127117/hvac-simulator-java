package com.hvac.simulator.measurement;

import java.time.LocalDateTime;

/** 为 Gaia 1.1 测量层提供按时间步锁定顺序的随机抽样。 */
public interface RandomDrawSource {
    Gaia11RandomDraws draws(int step, LocalDateTime timestamp, boolean powerMeterActive);
}
