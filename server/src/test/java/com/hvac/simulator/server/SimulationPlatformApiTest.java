package com.hvac.simulator.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hvac.simulator.release.ModelReleaseCatalog;
import com.hvac.simulator.server.api.ApiErrorHandler;
import com.hvac.simulator.server.api.ModelReleaseController;
import com.hvac.simulator.server.api.SimulationRunController;
import com.hvac.simulator.server.application.ModelReleaseService;
import com.hvac.simulator.server.application.SimulationRunService;
import com.hvac.simulator.server.infrastructure.InMemorySimulationRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SimulationPlatformApiTest {
    private static final Pattern RUN_ID = Pattern.compile("\\\"runId\\\":\\\"([^\\\"]+)\\\"");
    private ExecutorService executor;
    private SimulationRunService runService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var catalog = new ModelReleaseCatalog();
        executor = Executors.newSingleThreadExecutor();
        runService = new SimulationRunService(
                catalog, new InMemorySimulationRunRepository(), executor);
        mvc = MockMvcBuilders.standaloneSetup(
                        new ModelReleaseController(new ModelReleaseService(catalog)),
                        new SimulationRunController(runService))
                .setControllerAdvice(new ApiErrorHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void exposesVersionSpecificRealParameters() throws Exception {
        mvc.perform(get("/api/model-releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value("gaia-1.0"))
                .andExpect(jsonPath("$[1].version").value("gaia-1.1"))
                .andExpect(jsonPath("$[1].outputFieldCount").value(30));

        mvc.perform(get("/api/model-releases/gaia-1.1/parameters").param("mode", "BASELINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameters[0].editable").value(false))
                .andExpect(jsonPath("$.parameters[0].readOnlyReason").value("基准模式锁定参数"));

        mvc.perform(get("/api/model-releases/gaia-1.1/parameters").param("mode", "SCENARIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameters[0].defaultValue").value(10000.0))
                .andExpect(jsonPath("$.parameters[0].editable").value(true))
                .andExpect(jsonPath("$.parameters[?(@.code == 'hvac.coolingSetpointC')].scope")
                        .value("COMMON"))
                .andExpect(jsonPath("$.parameters[?(@.code == 'measurement.sensorBias')].defaultValue")
                        .value(0.0))
                .andExpect(jsonPath("$.parameters[?(@.code == 'measurement.sensorBias')].scope")
                        .value("VERSION_SPECIFIC"));

        mvc.perform(get("/api/model-releases/gaia-1.0/parameters").param("mode", "SCENARIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameters[?(@.scope == 'VERSION_SPECIFIC')]").isEmpty());
    }

    @Test
    void createsRunsAndReturnsProgressSeriesAndRows() throws Exception {
        String body = mvc.perform(post("/api/simulation-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelVersion":"gaia-1.1","mode":"SCENARIO","seed":99,
                                 "overrides":{"hvac.coolingSetpointC":20.0}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        var matcher = RUN_ID.matcher(body);
        assertTrue(matcher.find());
        UUID runId = UUID.fromString(matcher.group(1));

        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (runService.view(runId).status().name().matches("QUEUED|RUNNING")
                && Instant.now().isBefore(deadline)) {
            Thread.onSpinWait();
        }
        var view = runService.view(runId);
        assertEquals("COMPLETED", view.status().name());
        assertEquals(20.0, view.overrides().get("hvac.coolingSetpointC"));
        assertEquals(10_080, view.completedSteps());

        mvc.perform(get("/api/simulation-runs/{runId}/series", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamps.length()").value(10_080))
                .andExpect(jsonPath("$.groups.length()").value(5))
                .andExpect(jsonPath("$.groups[3].series[1].code").value("measured_COP"));
        mvc.perform(get("/api/simulation-runs/{runId}/rows", runId)
                        .param("offset", "10").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10_080))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].length()").value(30));
    }

    @Test
    void returnsStableChineseErrors() throws Exception {
        mvc.perform(post("/api/simulation-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelVersion":"gaia-1.1","mode":"BASELINE",
                                 "overrides":{"hvac.coolingSetpointC":20.0}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.title").value("请求参数错误"))
                .andExpect(jsonPath("$.message").value("基准模式不允许修改参数"));
    }
}
