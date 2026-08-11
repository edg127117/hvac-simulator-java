package com.hvac.simulator.measurement;

/** 由同步测量值反算的冷量和 COP。 */
public record Gaia11DerivedMeasurements(double coolingKw, double cop) {}
