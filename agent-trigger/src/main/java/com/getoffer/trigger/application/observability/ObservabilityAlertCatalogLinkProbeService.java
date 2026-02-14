package com.getoffer.trigger.application.observability;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警目录链接探测服务：用于定时巡检 dashboard/runbook 的可达性。
 */
@Slf4j
@Component
public class ObservabilityAlertCatalogLinkProbeService {

    private static final String PLACEHOLDER_PREFIX = "TODO:";

    private final int httpTimeoutMs;

    public ObservabilityAlertCatalogLinkProbeService(
            @Value("${observability.alert-catalog.link-check.http-timeout-ms:3000}") int httpTimeoutMs) {
        this.httpTimeoutMs = Math.max(httpTimeoutMs, 500);
    }

    public ProbeSummary probe(List<Map<String, Object>> catalogRows) {
        if (catalogRows == null || catalogRows.isEmpty()) {
            return new ProbeSummary(0, 0, 0, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
        }
        int alertCount = 0;
        int checkedLinks = 0;
        int failedLinks = 0;
        List<String> issues = new ArrayList<>();
        Map<String, ProbeBucketCounter> envCounters = new LinkedHashMap<>();
        Map<String, ProbeBucketCounter> moduleCounters = new LinkedHashMap<>();
        for (Map<String, Object> row : catalogRows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            alertCount++;
            String env = normalizeDimension(text(row.get("env")));
            String module = normalizeDimension(text(row.get("module")));
            ProbeResult dashboardResult = probeLink(row, "dashboard");
            checkedLinks++;
            recordDimension(envCounters, env, dashboardResult.healthy());
            recordDimension(moduleCounters, module, dashboardResult.healthy());
            if (!dashboardResult.healthy()) {
                failedLinks++;
                issues.add(dashboardResult.issue());
            }
            ProbeResult runbookResult = probeLink(row, "runbook");
            checkedLinks++;
            recordDimension(envCounters, env, runbookResult.healthy());
            recordDimension(moduleCounters, module, runbookResult.healthy());
            if (!runbookResult.healthy()) {
                failedLinks++;
                issues.add(runbookResult.issue());
            }
        }
        return new ProbeSummary(
                alertCount,
                checkedLinks,
                failedLinks,
                issues,
                toBucketStats(envCounters),
                toBucketStats(moduleCounters)
        );
    }

    private ProbeResult probeLink(Map<String, Object> row, String fieldName) {
        String alertName = text(row.get("alertName"));
        String link = text(row.get(fieldName));
        String normalizedAlertName = StringUtils.defaultIfBlank(alertName, "unknown-alert");
        if (StringUtils.isBlank(link)) {
            return ProbeResult.fail(normalizedAlertName + "." + fieldName + "=blank");
        }
        if (StringUtils.startsWithIgnoreCase(link, PLACEHOLDER_PREFIX)) {
            return ProbeResult.fail(normalizedAlertName + "." + fieldName + "=placeholder");
        }
        if (isHttpUrl(link)) {
            return probeHttpLink(normalizedAlertName, fieldName, link);
        }
        return probeLocalPath(normalizedAlertName, fieldName, link);
    }

    private ProbeResult probeHttpLink(String alertName, String fieldName, String link) {
        HttpProbeResult head = probeHttp(link, "HEAD");
        if (head.ok()) {
            return ProbeResult.ok();
        }
        if (head.statusCode() == HttpURLConnection.HTTP_BAD_METHOD || head.statusCode() == HttpURLConnection.HTTP_NOT_IMPLEMENTED) {
            HttpProbeResult get = probeHttp(link, "GET");
            if (get.ok()) {
                return ProbeResult.ok();
            }
            return ProbeResult.fail(alertName + "." + fieldName + "=http_status_" + get.statusCode());
        }
        if (head.statusCode() > 0) {
            return ProbeResult.fail(alertName + "." + fieldName + "=http_status_" + head.statusCode());
        }
        return ProbeResult.fail(alertName + "." + fieldName + "=http_error");
    }

    private ProbeResult probeLocalPath(String alertName, String fieldName, String link) {
        try {
            Path path = Paths.get(link);
            if (!path.isAbsolute()) {
                path = Paths.get("").toAbsolutePath().resolve(path).normalize();
            }
            if (Files.exists(path)) {
                return ProbeResult.ok();
            }
            return ProbeResult.fail(alertName + "." + fieldName + "=missing_local_path");
        } catch (InvalidPathException ex) {
            return ProbeResult.fail(alertName + "." + fieldName + "=invalid_local_path");
        }
    }

    private HttpProbeResult probeHttp(String link, String method) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(link);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(httpTimeoutMs);
            connection.setReadTimeout(httpTimeoutMs);
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(true);
            int statusCode = connection.getResponseCode();
            boolean ok = statusCode >= 200 && statusCode < 400;
            return new HttpProbeResult(ok, statusCode);
        } catch (IOException ex) {
            log.debug("Alert catalog link probe failed. link={}, method={}, error={}", link, method, ex.getMessage());
            return new HttpProbeResult(false, -1);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isHttpUrl(String value) {
        return StringUtils.startsWithIgnoreCase(value, "http://")
                || StringUtils.startsWithIgnoreCase(value, "https://");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void recordDimension(Map<String, ProbeBucketCounter> counters, String key, boolean healthy) {
        if (counters == null) {
            return;
        }
        String normalizedKey = normalizeDimension(key);
        ProbeBucketCounter counter = counters.computeIfAbsent(normalizedKey, ignored -> new ProbeBucketCounter());
        counter.checked += 1;
        if (!healthy) {
            counter.failed += 1;
        }
    }

    private Map<String, ProbeBucketStats> toBucketStats(Map<String, ProbeBucketCounter> counters) {
        if (counters == null || counters.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, ProbeBucketStats> result = new LinkedHashMap<>();
        for (Map.Entry<String, ProbeBucketCounter> entry : counters.entrySet()) {
            ProbeBucketCounter counter = entry.getValue();
            if (counter == null) {
                continue;
            }
            result.put(entry.getKey(), new ProbeBucketStats(
                    counter.checked,
                    counter.failed,
                    calculateFailureRate(counter.failed, counter.checked)
            ));
        }
        return result;
    }

    private double calculateFailureRate(int failed, int checked) {
        if (checked <= 0) {
            return 0D;
        }
        double rate = (failed * 100.0D) / checked;
        return Math.round(rate * 100.0D) / 100.0D;
    }

    private String normalizeDimension(String value) {
        String text = StringUtils.trimToEmpty(value);
        return StringUtils.isBlank(text) ? "unknown" : text.toLowerCase();
    }

    public record ProbeSummary(int alertCount,
                               int checkedLinks,
                               int failedLinks,
                               List<String> issues,
                               Map<String, ProbeBucketStats> envStats,
                               Map<String, ProbeBucketStats> moduleStats) {
        public ProbeSummary {
            issues = issues == null ? List.of() : List.copyOf(issues);
            envStats = envStats == null ? Map.of() : Map.copyOf(envStats);
            moduleStats = moduleStats == null ? Map.of() : Map.copyOf(moduleStats);
        }

        public boolean hasFailures() {
            return failedLinks > 0;
        }
    }

    public record ProbeBucketStats(int checkedLinks, int failedLinks, double failureRate) {
    }

    private static final class ProbeBucketCounter {
        private int checked;
        private int failed;
    }

    private record ProbeResult(boolean healthy, String issue) {
        private static ProbeResult ok() {
            return new ProbeResult(true, "");
        }

        private static ProbeResult fail(String issue) {
            return new ProbeResult(false, issue);
        }
    }

    private record HttpProbeResult(boolean ok, int statusCode) {
    }
}
