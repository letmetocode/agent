package com.getoffer.test;

import com.getoffer.trigger.application.observability.ObservabilityAlertCatalogLinkProbeService;
import com.getoffer.trigger.application.observability.ObservabilityAlertCatalogProbeStateStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObservabilityAlertCatalogProbeStateStoreTest {

    @Test
    public void shouldTrackTrendAndBuckets() {
        ObservabilityAlertCatalogProbeStateStore store = new ObservabilityAlertCatalogProbeStateStore(true, null);

        store.record(new ObservabilityAlertCatalogLinkProbeService.ProbeSummary(
                2,
                4,
                0,
                List.of(),
                Map.of("prod", new ObservabilityAlertCatalogLinkProbeService.ProbeBucketStats(4, 0, 0D)),
                Map.of("planner", new ObservabilityAlertCatalogLinkProbeService.ProbeBucketStats(4, 0, 0D))
        ));

        store.record(new ObservabilityAlertCatalogLinkProbeService.ProbeSummary(
                2,
                4,
                2,
                List.of("PlannerFallbackRatioCriticalProd.dashboard=http_error"),
                Map.of("prod", new ObservabilityAlertCatalogLinkProbeService.ProbeBucketStats(4, 2, 50D)),
                Map.of("planner", new ObservabilityAlertCatalogLinkProbeService.ProbeBucketStats(4, 2, 50D))
        ));

        Map<String, Object> payload = store.toPayload();
        assertEquals(true, payload.get("enabled"));
        assertEquals("WARN", payload.get("status"));
        assertEquals(50D, payload.get("failureRate"));
        assertEquals("UP", payload.get("failureRateTrend"));
        assertEquals(1, payload.get("issueCount"));
        assertEquals(2, ((List<?>) payload.get("recentRuns")).size());

        @SuppressWarnings("unchecked")
        Map<String, Object> envStats = (Map<String, Object>) payload.get("envStats");
        assertEquals(1, envStats.size());
    }

    @Test
    public void shouldKeepSnapshotWhenDisabled() {
        ObservabilityAlertCatalogProbeStateStore store = new ObservabilityAlertCatalogProbeStateStore(true, null);
        store.record(new ObservabilityAlertCatalogLinkProbeService.ProbeSummary(
                1,
                2,
                0,
                List.of(),
                Map.of(),
                Map.of()
        ));

        store.markDisabled();
        Map<String, Object> payload = store.toPayload();
        assertEquals("DISABLED", payload.get("status"));
        assertEquals(2, payload.get("checkedLinks"));
    }

    @Test
    public void shouldSupportWindowFilterAndTrendThreshold() {
        ObservabilityAlertCatalogProbeStateStore store = new ObservabilityAlertCatalogProbeStateStore(true, 12, 15D, null);
        store.record(new ObservabilityAlertCatalogLinkProbeService.ProbeSummary(
                1,
                10,
                0,
                List.of(),
                Map.of(),
                Map.of()
        ));
        store.record(new ObservabilityAlertCatalogLinkProbeService.ProbeSummary(
                1,
                10,
                1,
                List.of(),
                Map.of(),
                Map.of()
        ));
        store.record(new ObservabilityAlertCatalogLinkProbeService.ProbeSummary(
                1,
                10,
                2,
                List.of(),
                Map.of(),
                Map.of()
        ));

        Map<String, Object> fullPayload = store.toPayload();
        assertEquals("FLAT", fullPayload.get("failureRateTrend"));
        assertEquals(3, ((List<?>) fullPayload.get("recentRuns")).size());

        Map<String, Object> windowPayload = store.toPayload(2);
        assertEquals("FLAT", windowPayload.get("failureRateTrend"));
        assertEquals(2, ((List<?>) windowPayload.get("recentRuns")).size());

        Map<String, Object> singlePayload = store.toPayload(1);
        assertEquals("NA", singlePayload.get("failureRateTrend"));
        assertEquals(1, ((List<?>) singlePayload.get("recentRuns")).size());
    }
}
