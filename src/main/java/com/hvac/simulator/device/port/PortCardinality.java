package com.hvac.simulator.device.port;

public enum PortCardinality {
    OPTIONAL_SINGLE(0, 1),
    REQUIRED_SINGLE(1, 1),
    OPTIONAL_MULTIPLE(0, Integer.MAX_VALUE),
    REQUIRED_MULTIPLE(1, Integer.MAX_VALUE);

    private final int minimum;
    private final int maximum;

    PortCardinality(int minimum, int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public boolean accepts(int connectionCount) {
        return connectionCount >= minimum && connectionCount <= maximum;
    }

    public boolean required() {
        return minimum > 0;
    }

    public boolean multiple() {
        return maximum > 1;
    }
}
