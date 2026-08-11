package com.hvac.simulator.server.delivery;

/** 中央空调平台单点上报载荷。 */
public record CentralHvacPoint(
        String buildingId, String deviceId, String pointCode, double value, long timestamp) {}
