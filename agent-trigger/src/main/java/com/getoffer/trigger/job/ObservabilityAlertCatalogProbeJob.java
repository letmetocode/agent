package com.getoffer.trigger.job;

import com.getoffer.trigger.application.observability.ObservabilityAlertCatalogLinkProbeService;
import com.getoffer.trigger.application.observability.ObservabilityAlertCatalogProbeStateStore;
import com.getoffer.trigger.http.ObservabilityAlertCatalogController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 告警目录链接健康巡检作业。
 */
@Slf4j
@Component
public class ObservabilityAlertCatalogProbeJob {

    private final ObservabilityAlertCatalogController catalogController;
    private final ObservabilityAlertCatalogLinkProbeService linkProbeService;
    private final ObservabilityAlertCatalogProbeStateStore probeStateStore;
    private final boolean enabled;
    private final int maxIssueLogCount;

    public ObservabilityAlertCatalogProbeJob(ObservabilityAlertCatalogController catalogController,
                                             ObservabilityAlertCatalogLinkProbeService linkProbeService,
                                             ObservabilityAlertCatalogProbeStateStore probeStateStore,
                                             @Value("${observability.alert-catalog.link-check.enabled:false}") boolean enabled,
                                             @Value("${observability.alert-catalog.link-check.max-issue-log-count:20}") int maxIssueLogCount) {
        this.catalogController = catalogController;
        this.linkProbeService = linkProbeService;
        this.probeStateStore = probeStateStore;
        this.enabled = enabled;
        this.maxIssueLogCount = Math.max(maxIssueLogCount, 1);
    }

    @Scheduled(
            fixedDelayString = "${observability.alert-catalog.link-check.interval-ms:300000}",
            scheduler = "daemonScheduler"
    )
    public void probeLinks() {
        if (!enabled) {
            probeStateStore.markDisabled();
            return;
        }
        List<Map<String, Object>> rows = catalogController.getCatalogSnapshot();
        ObservabilityAlertCatalogLinkProbeService.ProbeSummary summary = linkProbeService.probe(rows);
        probeStateStore.record(summary);
        if (!summary.hasFailures()) {
            log.info("Alert catalog link probe passed. alerts={}, checkedLinks={}",
                    summary.alertCount(),
                    summary.checkedLinks());
            return;
        }
        String issuePreview = summary.issues()
                .stream()
                .limit(maxIssueLogCount)
                .collect(Collectors.joining(", "));
        log.warn("Alert catalog link probe found issues. alerts={}, checkedLinks={}, failedLinks={}, issues={}",
                summary.alertCount(),
                summary.checkedLinks(),
                summary.failedLinks(),
                issuePreview);
    }
}
