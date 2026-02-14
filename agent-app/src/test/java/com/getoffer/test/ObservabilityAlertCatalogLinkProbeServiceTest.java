package com.getoffer.test;

import com.getoffer.trigger.application.observability.ObservabilityAlertCatalogLinkProbeService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObservabilityAlertCatalogLinkProbeServiceTest {

    @Test
    public void shouldDetectPlaceholderAndMissingLocalPath() {
        ObservabilityAlertCatalogLinkProbeService service = new ObservabilityAlertCatalogLinkProbeService(1000);
        List<Map<String, Object>> rows = List.of(
                Map.of(
                        "alertName", "PlannerFallbackRatioCriticalProd",
                        "module", "planner",
                        "env", "prod",
                        "dashboard", "TODO: replace-me",
                        "runbook", "docs/dev-ops/observability/not-exists-runbook.md"
                )
        );

        ObservabilityAlertCatalogLinkProbeService.ProbeSummary summary = service.probe(rows);
        assertEquals(1, summary.alertCount());
        assertEquals(2, summary.checkedLinks());
        assertEquals(2, summary.failedLinks());
        assertEquals(2, summary.envStats().get("prod").checkedLinks());
        assertEquals(2, summary.envStats().get("prod").failedLinks());
        assertEquals(100D, summary.envStats().get("prod").failureRate());
        assertEquals(2, summary.moduleStats().get("planner").checkedLinks());
        assertEquals(2, summary.moduleStats().get("planner").failedLinks());
        assertTrue(summary.issues().contains("PlannerFallbackRatioCriticalProd.dashboard=placeholder"));
        assertTrue(summary.issues().contains("PlannerFallbackRatioCriticalProd.runbook=missing_local_path"));
    }

    @Test
    public void shouldPassWhenLocalPathsExist() {
        ObservabilityAlertCatalogLinkProbeService service = new ObservabilityAlertCatalogLinkProbeService(1000);
        String dashboardPath = resolveExistingPath(
                "docs/dev-ops/observability/README.md",
                "../docs/dev-ops/observability/README.md"
        );
        String runbookPath = resolveExistingPath(
                "docs/dev-ops/observability/sse-alert-runbook.md",
                "../docs/dev-ops/observability/sse-alert-runbook.md"
        );
        List<Map<String, Object>> rows = List.of(
                Map.of(
                        "alertName", "SsePushFailRatioCriticalProd",
                        "module", "sse",
                        "env", "prod",
                        "dashboard", dashboardPath,
                        "runbook", runbookPath
                )
        );

        ObservabilityAlertCatalogLinkProbeService.ProbeSummary summary = service.probe(rows);
        assertEquals(1, summary.alertCount());
        assertEquals(2, summary.checkedLinks());
        assertEquals(0, summary.failedLinks());
        assertEquals(2, summary.envStats().get("prod").checkedLinks());
        assertEquals(0, summary.envStats().get("prod").failedLinks());
        assertEquals(0D, summary.envStats().get("prod").failureRate());
        assertEquals(2, summary.moduleStats().get("sse").checkedLinks());
        assertEquals(0, summary.moduleStats().get("sse").failedLinks());
        assertTrue(summary.issues().isEmpty());
    }

    @Test
    public void shouldReturnEmptySummaryWhenRowsEmpty() {
        ObservabilityAlertCatalogLinkProbeService service = new ObservabilityAlertCatalogLinkProbeService(1000);
        ObservabilityAlertCatalogLinkProbeService.ProbeSummary summary = service.probe(List.of());
        assertEquals(0, summary.alertCount());
        assertEquals(0, summary.checkedLinks());
        assertEquals(0, summary.failedLinks());
        assertTrue(summary.envStats().isEmpty());
        assertTrue(summary.moduleStats().isEmpty());
    }

    private String resolveExistingPath(String... candidates) {
        for (String candidate : candidates) {
            Path path = Paths.get(candidate).toAbsolutePath().normalize();
            if (Files.exists(path)) {
                return path.toString();
            }
        }
        throw new IllegalStateException("No existing path found for observability test fixture");
    }
}
