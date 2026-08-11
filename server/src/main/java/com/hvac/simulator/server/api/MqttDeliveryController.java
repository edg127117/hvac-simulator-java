package com.hvac.simulator.server.api;

import com.hvac.simulator.server.api.dto.MqttDeliveryDtos.CreateRequest;
import com.hvac.simulator.server.api.dto.MqttDeliveryDtos.Created;
import com.hvac.simulator.server.api.dto.MqttDeliveryDtos.View;
import com.hvac.simulator.server.delivery.MqttDeliveryService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation-runs/{runId}/mqtt-deliveries")
public class MqttDeliveryController {
    private final MqttDeliveryService service;

    public MqttDeliveryController(MqttDeliveryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Created> create(
            @PathVariable("runId") UUID runId, @RequestBody CreateRequest request) {
        return ResponseEntity.accepted().body(service.create(runId, request));
    }

    @GetMapping("/{deliveryId}")
    public View view(
            @PathVariable("runId") UUID runId,
            @PathVariable("deliveryId") UUID deliveryId) {
        return service.view(runId, deliveryId);
    }
}
