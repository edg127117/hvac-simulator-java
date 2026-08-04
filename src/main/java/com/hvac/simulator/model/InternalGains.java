package com.hvac.simulator.model;

/** 显热和潜热成对结果，单位均为 W；正值表示热量进入室内。 */
public record InternalGains(double sensibleW, double latentW) {}
