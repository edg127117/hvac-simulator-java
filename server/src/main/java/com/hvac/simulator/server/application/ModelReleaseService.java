package com.hvac.simulator.server.application;

import com.hvac.simulator.release.ModelParameterDescriptor;
import com.hvac.simulator.release.ModelReleaseCatalog;
import com.hvac.simulator.release.ModelVersion;
import com.hvac.simulator.server.api.dto.ModelReleaseDtos.Parameter;
import com.hvac.simulator.server.api.dto.ModelReleaseDtos.ParameterCatalog;
import com.hvac.simulator.server.api.dto.ModelReleaseDtos.ReleaseSummary;
import com.hvac.simulator.server.domain.SimulationMode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelReleaseService {
    private final ModelReleaseCatalog catalog;

    public ModelReleaseService(ModelReleaseCatalog catalog) {
        this.catalog = catalog;
    }

    public List<ReleaseSummary> releases() {
        return catalog.releases().stream()
                .map(release -> new ReleaseSummary(
                        release.version().code(), release.displayName(), release.outputFieldCount()))
                .toList();
    }

    public ParameterCatalog parameters(String versionCode, SimulationMode mode) {
        var release = catalog.release(ModelVersion.parse(versionCode));
        return new ParameterCatalog(
                release.version().code(), release.displayName(), mode.name(),
                release.parameters().stream().map(parameter -> toDto(parameter, mode)).toList());
    }

    private Parameter toDto(ModelParameterDescriptor parameter, SimulationMode mode) {
        boolean editable = mode == SimulationMode.SCENARIO && parameter.editable();
        String reason = editable ? null : mode == SimulationMode.BASELINE
                ? "基准模式锁定参数" : parameter.readOnlyReason();
        return new Parameter(
                parameter.code(), parameter.label(), parameter.group(), parameter.unit(),
                parameter.valueType(), parameter.defaultValue(), parameter.minimum(), parameter.maximum(),
                editable, reason);
    }
}
