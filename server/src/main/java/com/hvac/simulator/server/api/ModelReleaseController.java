package com.hvac.simulator.server.api;

import com.hvac.simulator.server.api.dto.ModelReleaseDtos.ParameterCatalog;
import com.hvac.simulator.server.api.dto.ModelReleaseDtos.ReleaseSummary;
import com.hvac.simulator.server.application.ModelReleaseService;
import com.hvac.simulator.server.domain.SimulationMode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-releases")
public class ModelReleaseController {
    private final ModelReleaseService service;

    public ModelReleaseController(ModelReleaseService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReleaseSummary> releases() {
        return service.releases();
    }

    @GetMapping("/{version}/parameters")
    public ParameterCatalog parameters(
            @PathVariable("version") String version,
            @RequestParam(name = "mode", defaultValue = "SCENARIO") SimulationMode mode) {
        return service.parameters(version, mode);
    }
}
