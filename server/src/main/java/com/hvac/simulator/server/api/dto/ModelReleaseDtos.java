package com.hvac.simulator.server.api.dto;

import com.hvac.simulator.release.ParameterScope;
import com.hvac.simulator.release.ParameterValueType;
import java.util.List;

public final class ModelReleaseDtos {
    private ModelReleaseDtos() {}

    public record ReleaseSummary(String version, String displayName, int outputFieldCount) {}

    public record Parameter(
            String code, String label, String group, String unit,
            ParameterValueType valueType, double defaultValue, double minimum,
            double maximum, ParameterScope scope, boolean editable, String readOnlyReason) {}

    public record ParameterCatalog(
            String version, String displayName, String mode, List<Parameter> parameters) {}
}
