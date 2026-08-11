package com.hvac.simulator.server.api;

import com.hvac.simulator.server.api.dto.SimulationRunDtos.CreateRequest;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.Created;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.Rows;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.RunView;
import com.hvac.simulator.server.api.dto.SimulationRunDtos.SeriesResponse;
import com.hvac.simulator.server.application.SimulationRunService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation-runs")
public class SimulationRunController {
    private final SimulationRunService service;

    public SimulationRunController(SimulationRunService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Created> create(@RequestBody CreateRequest request) {
        return ResponseEntity.accepted().body(service.create(request));
    }

    @GetMapping("/{runId}")
    public RunView view(@PathVariable("runId") UUID runId) {
        return service.view(runId);
    }

    @GetMapping("/{runId}/series")
    public SeriesResponse series(@PathVariable("runId") UUID runId) {
        return service.series(runId);
    }

    @GetMapping("/{runId}/rows")
    public Rows rows(
            @PathVariable("runId") UUID runId,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestParam(name = "limit", defaultValue = "200") int limit) {
        return service.rows(runId, offset, limit);
    }
}
