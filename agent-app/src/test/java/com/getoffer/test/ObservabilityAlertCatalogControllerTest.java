package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.trigger.http.ObservabilityAlertCatalogController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ObservabilityAlertCatalogControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        ObservabilityAlertCatalogController controller = new ObservabilityAlertCatalogController(
                new ObjectMapper(),
                new ClassPathResource("observability/alert-catalog.json"),
                "",
                "",
                null
        );
        controller.init();
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void shouldReturnAlertCatalog() throws Exception {
        mockMvc.perform(get("/api/observability/alerts/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.length()").isNumber())
                .andExpect(jsonPath("$.data[0].module").exists())
                .andExpect(jsonPath("$.data[0].alertName").exists())
                .andExpect(jsonPath("$.data[0].runbook").exists());
    }

    @Test
    public void shouldReplaceDashboardPlaceholderByEnvBaseUrl() throws Exception {
        String catalogWithPlaceholder = """
                [
                  {
                    "module": "planner",
                    "alertName": "PlannerFallbackRatioCriticalProd",
                    "severity": "critical",
                    "env": "prod",
                    "summary": "Planner fallback 比例持续偏高",
                    "ruleFile": "docs/dev-ops/observability/prometheus/planner-alert-rules.yml",
                    "runbook": "docs/dev-ops/observability/planner-alert-runbook.md",
                    "dashboard": "TODO: replace-with-prod-dashboard-url"
                  },
                  {
                    "module": "planner",
                    "alertName": "PlannerFallbackSpikeWarningStaging",
                    "severity": "warning",
                    "env": "staging",
                    "summary": "Planner fallback 绝对值升高",
                    "ruleFile": "docs/dev-ops/observability/prometheus/planner-alert-rules.yml",
                    "runbook": "docs/dev-ops/observability/planner-alert-runbook.md",
                    "dashboard": "TODO: replace-with-staging-dashboard-url"
                  }
                ]
                """;

        ObservabilityAlertCatalogController controller = new ObservabilityAlertCatalogController(
                new ObjectMapper(),
                new ByteArrayResource(catalogWithPlaceholder.getBytes(StandardCharsets.UTF_8)),
                "https://grafana.prod.example.com/d/ops",
                "https://grafana.staging.example.com/d/ops",
                null
        );
        controller.init();
        MockMvc customMvc = MockMvcBuilders.standaloneSetup(controller).build();

        customMvc.perform(get("/api/observability/alerts/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dashboard").value("https://grafana.prod.example.com/d/ops"))
                .andExpect(jsonPath("$.data[1].dashboard").value("https://grafana.staging.example.com/d/ops"));
    }

    @Test
    public void shouldReturnProbeStatusSnapshot() throws Exception {
        mockMvc.perform(get("/api/observability/alerts/probe-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.issueCount").value(0));

        mockMvc.perform(get("/api/observability/alerts/probe-status").param("window", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }
}
