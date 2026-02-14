package com.getoffer.trigger.application.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 告警目录链接巡检结果快照存储。
 */
@Component
public class ObservabilityAlertCatalogProbeStateStore {

    private final boolean enabled;
    private final int maxHistorySize;
    private final double trendDeltaThreshold;
    private final AtomicReference<ProbeState> latestState;

    @Autowired
    public ObservabilityAlertCatalogProbeStateStore(
            @Value("${observability.alert-catalog.link-check.enabled:false}") boolean enabled,
            @Value("${observability.alert-catalog.link-check.history-size:12}") int maxHistorySize,
            @Value("${observability.alert-catalog.link-check.trend-delta-threshold:1.0}") double trendDeltaThreshold) {
        this(enabled, maxHistorySize, trendDeltaThreshold, null);
    }

    public ObservabilityAlertCatalogProbeStateStore(boolean enabled, ProbeState initialState) {
        this(enabled, 12, 1.0D, initialState);
    }

    public ObservabilityAlertCatalogProbeStateStore(boolean enabled,
                                                    int maxHistorySize,
                                                    double trendDeltaThreshold,
                                                    ProbeState initialState) {
        this.enabled = enabled;
        this.maxHistorySize = Math.max(maxHistorySize, 1);
        this.trendDeltaThreshold = Math.max(trendDeltaThreshold, 0D);
        this.latestState = new AtomicReference<>(initialState == null
                ? (enabled ? ProbeState.idle() : ProbeState.disabled(null))
                : initialState);
    }

    public void record(ObservabilityAlertCatalogLinkProbeService.ProbeSummary summary) {
        ProbeState previous = latestState.get();
        ProbeState next = ProbeState.fromSummary(
                summary,
                OffsetDateTime.now().toString(),
                previous,
                maxHistorySize,
                trendDeltaThreshold
        );
        latestState.set(next);
    }

    public void markDisabled() {
        latestState.updateAndGet(ProbeState::disabled);
    }

    public ProbeState snapshot() {
        return latestState.get();
    }

    public Map<String, Object> toPayload() {
        return toPayload(null);
    }

    public Map<String, Object> toPayload(Integer windowSize) {
        ProbeState state = latestState.get();
        List<RunSnapshot> recentRuns = sliceRecentRuns(state.recentRuns, windowSize);
        String trend = ProbeState.resolveFailureRateTrend(recentRuns, trendDeltaThreshold);
        return buildPayload(state, recentRuns, trend);
    }

    private List<RunSnapshot> sliceRecentRuns(List<RunSnapshot> recentRuns, Integer windowSize) {
        if (recentRuns == null || recentRuns.isEmpty()) {
            return List.of();
        }
        if (windowSize == null || windowSize <= 0 || windowSize >= recentRuns.size()) {
            return List.copyOf(recentRuns);
        }
        int fromIndex = recentRuns.size() - windowSize;
        return List.copyOf(recentRuns.subList(fromIndex, recentRuns.size()));
    }

    private Map<String, Object> buildPayload(ProbeState state, List<RunSnapshot> recentRuns, String trend) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", enabled);
        payload.put("status", state.status);
        payload.put("lastRunAt", state.lastRunAt);
        payload.put("alertCount", state.alertCount);
        payload.put("checkedLinks", state.checkedLinks);
        payload.put("failedLinks", state.failedLinks);
        payload.put("failureRate", state.failureRate);
        payload.put("failureRateTrend", trend);
        payload.put("issueCount", state.issues == null ? 0 : state.issues.size());
        payload.put("issues", state.issues == null ? List.of() : state.issues);
        payload.put("envStats", state.envStats == null ? Map.of() : state.envStats);
        payload.put("moduleStats", state.moduleStats == null ? Map.of() : state.moduleStats);
        payload.put("recentRuns", recentRuns == null ? List.of() : recentRuns);
        return payload;
    }

    public static final class ProbeState {
        private final String status;
        private final String lastRunAt;
        private final int alertCount;
        private final int checkedLinks;
        private final int failedLinks;
        private final double failureRate;
        private final String failureRateTrend;
        private final List<String> issues;
        private final Map<String, BucketSnapshot> envStats;
        private final Map<String, BucketSnapshot> moduleStats;
        private final List<RunSnapshot> recentRuns;

        private ProbeState(String status,
                           String lastRunAt,
                           int alertCount,
                           int checkedLinks,
                           int failedLinks,
                           double failureRate,
                           String failureRateTrend,
                           List<String> issues,
                           Map<String, BucketSnapshot> envStats,
                           Map<String, BucketSnapshot> moduleStats,
                           List<RunSnapshot> recentRuns) {
            this.status = status;
            this.lastRunAt = lastRunAt;
            this.alertCount = alertCount;
            this.checkedLinks = checkedLinks;
            this.failedLinks = failedLinks;
            this.failureRate = failureRate;
            this.failureRateTrend = failureRateTrend;
            this.issues = issues == null ? List.of() : List.copyOf(issues);
            this.envStats = envStats == null ? Map.of() : Map.copyOf(envStats);
            this.moduleStats = moduleStats == null ? Map.of() : Map.copyOf(moduleStats);
            this.recentRuns = recentRuns == null ? List.of() : List.copyOf(recentRuns);
        }

        private static ProbeState fromSummary(ObservabilityAlertCatalogLinkProbeService.ProbeSummary summary,
                                              String executedAt,
                                              ProbeState previous,
                                              int maxHistorySize,
                                              double trendDeltaThreshold) {
            int alertCount = summary == null ? 0 : summary.alertCount();
            int checkedLinks = summary == null ? 0 : summary.checkedLinks();
            int failedLinks = summary == null ? 0 : summary.failedLinks();
            double failureRate = calculateFailureRate(failedLinks, checkedLinks);
            List<String> issues = summary == null || summary.issues() == null
                    ? List.of()
                    : new ArrayList<>(summary.issues());
            Map<String, BucketSnapshot> envStats = toBucketSnapshots(summary == null ? Map.of() : summary.envStats());
            Map<String, BucketSnapshot> moduleStats = toBucketSnapshots(summary == null ? Map.of() : summary.moduleStats());
            String status = failedLinks > 0 ? "WARN" : "PASS";

            List<RunSnapshot> runs = previous == null
                    ? new ArrayList<>()
                    : new ArrayList<>(previous.recentRuns);
            runs.add(new RunSnapshot(executedAt, status, checkedLinks, failedLinks, failureRate));
            while (runs.size() > Math.max(maxHistorySize, 1)) {
                runs.remove(0);
            }
            String failureRateTrend = resolveFailureRateTrend(runs, trendDeltaThreshold);

            return new ProbeState(
                    status,
                    executedAt,
                    alertCount,
                    checkedLinks,
                    failedLinks,
                    failureRate,
                    failureRateTrend,
                    issues,
                    envStats,
                    moduleStats,
                    runs
            );
        }

        private static ProbeState idle() {
            return new ProbeState("IDLE", "", 0, 0, 0, 0D, "NA", List.of(), Map.of(), Map.of(), List.of());
        }

        private static ProbeState disabled(ProbeState previous) {
            if (previous == null) {
                return new ProbeState("DISABLED", "", 0, 0, 0, 0D, "NA", List.of(), Map.of(), Map.of(), List.of());
            }
            return new ProbeState(
                    "DISABLED",
                    previous.lastRunAt,
                    previous.alertCount,
                    previous.checkedLinks,
                    previous.failedLinks,
                    previous.failureRate,
                    previous.failureRateTrend,
                    previous.issues,
                    previous.envStats,
                    previous.moduleStats,
                    previous.recentRuns
            );
        }

        private static Map<String, BucketSnapshot> toBucketSnapshots(
                Map<String, ObservabilityAlertCatalogLinkProbeService.ProbeBucketStats> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            Map<String, BucketSnapshot> result = new LinkedHashMap<>();
            for (Map.Entry<String, ObservabilityAlertCatalogLinkProbeService.ProbeBucketStats> entry : source.entrySet()) {
                String key = entry.getKey();
                ObservabilityAlertCatalogLinkProbeService.ProbeBucketStats value = entry.getValue();
                if (value == null) {
                    continue;
                }
                result.put(key, new BucketSnapshot(value.checkedLinks(), value.failedLinks(), value.failureRate()));
            }
            return result;
        }

        private static double calculateFailureRate(int failedLinks, int checkedLinks) {
            if (checkedLinks <= 0) {
                return 0D;
            }
            double rate = (failedLinks * 100.0D) / checkedLinks;
            return Math.round(rate * 100.0D) / 100.0D;
        }

        private static String resolveFailureRateTrend(List<RunSnapshot> runs, double deltaThreshold) {
            if (runs == null || runs.size() < 2) {
                return "NA";
            }
            RunSnapshot last = runs.get(runs.size() - 1);
            RunSnapshot previous = runs.get(runs.size() - 2);
            double delta = last.failureRate() - previous.failureRate();
            double normalizedThreshold = Math.max(deltaThreshold, 0D);
            if (delta > normalizedThreshold) {
                return "UP";
            }
            if (delta < -normalizedThreshold) {
                return "DOWN";
            }
            return "FLAT";
        }
    }

    public record BucketSnapshot(int checkedLinks, int failedLinks, double failureRate) {
    }

    public record RunSnapshot(String executedAt, String status, int checkedLinks, int failedLinks, double failureRate) {
    }
}
