package com.hvac.simulator.measurement;

import java.time.LocalDateTime;
import java.util.SplittableRandom;

/** 场景模式使用的可重复 Java 随机源；不承担 Python 基准逐值一致性。 */
public final class SeededRandomDrawSource implements RandomDrawSource {
    private final SplittableRandom random;
    private int expectedStep;

    public SeededRandomDrawSource(long seed) {
        random = new SplittableRandom(seed);
    }

    @Override
    public Gaia11RandomDraws draws(int step, LocalDateTime timestamp, boolean powerMeterActive) {
        if (step != expectedStep++) {
            throw new IllegalStateException("随机源必须按连续时间步消费");
        }
        Double power = powerMeterActive ? random.nextDouble(-1.0, 1.0) : null;
        return new Gaia11RandomDraws(
                power, gaussian(), gaussian(), gaussian(), gaussian(), gaussian(), gaussian());
    }

    private double gaussian() {
        double u1 = Math.max(random.nextDouble(), Double.MIN_VALUE);
        double u2 = random.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }
}
