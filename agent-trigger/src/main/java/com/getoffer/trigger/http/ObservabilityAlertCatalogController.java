package com.getoffer.trigger.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.api.response.Response;
import com.getoffer.trigger.application.observability.ObservabilityAlertCatalogProbeStateStore;
import com.getoffer.types.enums.ResponseCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 观测告警目录接口：用于前端总览页提供规则与处置入口。
 */
@Slf4j
@RestController
@RequestMapping("/api/observability/alerts")
public class ObservabilityAlertCatalogController {

    private static final String ENV_PROD = "prod";
    private static final String ENV_STAGING = "staging";
    private static final String DASHBOARD_PLACEHOLDER_PREFIX = "TODO:";

    private final ObjectMapper objectMapper;
    private final Resource catalogResource;
    private final String prodDashboardBaseUrl;
    private final String stagingDashboardBaseUrl;
    private final ObservabilityAlertCatalogProbeStateStore probeStateStore;
    private volatile List<Map<String, Object>> cachedCatalog = Collections.emptyList();

    public ObservabilityAlertCatalogController(ObjectMapper objectMapper,
                                               @Value("classpath:observability/alert-catalog.json") Resource catalogResource) {
        this(objectMapper, catalogResource, "", "", null);
    }

    public ObservabilityAlertCatalogController(ObjectMapper objectMapper,
                                               @Value("classpath:observability/alert-catalog.json") Resource catalogResource,
                                               @Value("${observability.alert-catalog.dashboard.prod-base-url:}") String prodDashboardBaseUrl,
                                               @Value("${observability.alert-catalog.dashboard.staging-base-url:}") String stagingDashboardBaseUrl) {
        this(objectMapper, catalogResource, prodDashboardBaseUrl, stagingDashboardBaseUrl, null);
    }

    @Autowired
    public ObservabilityAlertCatalogController(ObjectMapper objectMapper,
                                               @Value("classpath:observability/alert-catalog.json") Resource catalogResource,
                                               @Value("${observability.alert-catalog.dashboard.prod-base-url:}") String prodDashboardBaseUrl,
                                               @Value("${observability.alert-catalog.dashboard.staging-base-url:}") String stagingDashboardBaseUrl,
                                               @Autowired(required = false) ObservabilityAlertCatalogProbeStateStore probeStateStore) {
        this.objectMapper = objectMapper;
        this.catalogResource = catalogResource;
        this.prodDashboardBaseUrl = StringUtils.trimToEmpty(prodDashboardBaseUrl);
        this.stagingDashboardBaseUrl = StringUtils.trimToEmpty(stagingDashboardBaseUrl);
        this.probeStateStore = probeStateStore == null
                ? new ObservabilityAlertCatalogProbeStateStore(false, null)
                : probeStateStore;
    }

    @PostConstruct
    public void init() {
        this.cachedCatalog = loadCatalog();
    }

    @GetMapping("/catalog")
    public Response<List<Map<String, Object>>> getCatalog() {
        return success(cachedCatalog);
    }

    @GetMapping("/probe-status")
    public Response<Map<String, Object>> getProbeStatus(
            @RequestParam(value = "window", required = false) Integer windowSize) {
        return success(probeStateStore.toPayload(windowSize));
    }

    public List<Map<String, Object>> getCatalogSnapshot() {
        if (cachedCatalog == null || cachedCatalog.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> snapshot = new ArrayList<>(cachedCatalog.size());
        for (Map<String, Object> row : cachedCatalog) {
            snapshot.add(row == null ? Collections.emptyMap() : new LinkedHashMap<>(row));
        }
        return snapshot;
    }

    private List<Map<String, Object>> loadCatalog() {
        if (catalogResource == null || !catalogResource.exists()) {
            log.warn("Observability alert catalog missing from classpath.");
            return Collections.emptyList();
        }
        try (InputStream inputStream = catalogResource.getInputStream()) {
            List<Map<String, Object>> rows = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
            List<Map<String, Object>> resolved = resolveDashboardPlaceholders(rows == null ? Collections.emptyList() : rows);
            inspectCatalogResolution(resolved);
            return resolved;
        } catch (Exception ex) {
            log.warn("Failed to load observability alert catalog. error={}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> resolveDashboardPlaceholders(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> resolved = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Map<String, Object> target = new LinkedHashMap<>(row);
            String env = toText(target.get("env"));
            String dashboard = toText(target.get("dashboard"));
            if (StringUtils.startsWithIgnoreCase(dashboard, DASHBOARD_PLACEHOLDER_PREFIX)) {
                String replacement = resolveDashboardBaseUrl(env);
                if (StringUtils.isNotBlank(replacement)) {
                    target.put("dashboard", replacement);
                }
            }
            resolved.add(target);
        }
        return resolved;
    }

    private void inspectCatalogResolution(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<String> unresolvedAlertNames = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String dashboard = toText(row.get("dashboard"));
            if (!StringUtils.startsWithIgnoreCase(dashboard, DASHBOARD_PLACEHOLDER_PREFIX)) {
                continue;
            }
            unresolvedAlertNames.add(toText(row.get("alertName")));
        }
        if (unresolvedAlertNames.isEmpty()) {
            log.info("Observability alert catalog loaded. all dashboard links resolved.");
            return;
        }
        log.warn("Observability alert catalog contains unresolved dashboard links. alerts={}, prodBaseSet={}, stagingBaseSet={}",
                unresolvedAlertNames,
                StringUtils.isNotBlank(prodDashboardBaseUrl),
                StringUtils.isNotBlank(stagingDashboardBaseUrl));
    }

    private String resolveDashboardBaseUrl(String env) {
        if (StringUtils.equalsIgnoreCase(env, ENV_PROD)) {
            return prodDashboardBaseUrl;
        }
        if (StringUtils.equalsIgnoreCase(env, ENV_STAGING)) {
            return stagingDashboardBaseUrl;
        }
        return "";
    }

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
