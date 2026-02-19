package com.getoffer.trigger.application.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 非 V3 接口废弃治理注册表查询服务。
 */
@Slf4j
@Service
public class DeprecationRegistryQueryService {

    private static final int MIN_NOTICE_WINDOW_DAYS = 30;
    private static final List<String> SUPPORTED_STATUS = List.of("ANNOUNCED", "MIGRATING", "REMOVED");

    private final ObjectMapper objectMapper;
    private final Resource registryResource;
    private volatile List<Map<String, Object>> cachedRegistry = Collections.emptyList();

    public DeprecationRegistryQueryService(ObjectMapper objectMapper,
                                           @Value("classpath:governance/deprecation-registry.json") Resource registryResource) {
        this.objectMapper = objectMapper;
        this.registryResource = registryResource;
    }

    @PostConstruct
    public void init() {
        this.cachedRegistry = loadRegistry();
    }

    public List<Map<String, Object>> list(String status, Boolean includeRemoved) {
        String normalizedStatus = normalizeStatus(status);
        boolean withRemoved = includeRemoved == null || includeRemoved;
        if (cachedRegistry == null || cachedRegistry.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : cachedRegistry) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String rowStatus = normalizeStatus(String.valueOf(row.getOrDefault("status", "")));
            if (!withRemoved && "REMOVED".equals(rowStatus)) {
                continue;
            }
            if (StringUtils.isNotBlank(normalizedStatus) && !normalizedStatus.equals(rowStatus)) {
                continue;
            }
            rows.add(new LinkedHashMap<>(row));
        }
        return rows;
    }

    public int minNoticeWindowDays() {
        return MIN_NOTICE_WINDOW_DAYS;
    }

    private List<Map<String, Object>> loadRegistry() {
        if (registryResource == null || !registryResource.exists()) {
            log.warn("Deprecation registry file missing from classpath.");
            return Collections.emptyList();
        }
        try (InputStream inputStream = registryResource.getInputStream()) {
            List<Map<String, Object>> source = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
            if (source == null || source.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> normalized = new ArrayList<>(source.size());
            for (Map<String, Object> row : source) {
                Map<String, Object> item = normalizeRow(row);
                if (!item.isEmpty()) {
                    normalized.add(item);
                }
            }
            return normalized;
        } catch (Exception ex) {
            log.warn("Failed to load deprecation registry. error={}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> normalizeRow(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> issues = new ArrayList<>();
        String status = normalizeStatus(text(source.get("status")));
        if (!SUPPORTED_STATUS.contains(status)) {
            issues.add("unsupported status: " + status);
        }

        String legacyPath = text(source.get("legacyPath"));
        if (StringUtils.isBlank(legacyPath)) {
            issues.add("legacyPath is required");
        }
        String migrationDoc = text(source.get("migrationDoc"));
        if (StringUtils.isBlank(migrationDoc)) {
            issues.add("migrationDoc is required");
        }

        LocalDate announcedAt = parseDate(text(source.get("announcedAt")), "announcedAt", issues);
        LocalDate sunsetAt = parseDate(text(source.get("sunsetAt")), "sunsetAt", issues);
        long noticeWindowDays = 0L;
        if (announcedAt != null && sunsetAt != null) {
            noticeWindowDays = ChronoUnit.DAYS.between(announcedAt, sunsetAt);
            if (noticeWindowDays < 0) {
                issues.add("sunsetAt before announcedAt");
            }
        }
        boolean meetsNoticeWindow = noticeWindowDays >= MIN_NOTICE_WINDOW_DAYS;
        if (!"REMOVED".equals(status) && !meetsNoticeWindow) {
            issues.add("notice window below baseline(" + MIN_NOTICE_WINDOW_DAYS + "d)");
        }
        long daysToSunset = sunsetAt == null ? Long.MIN_VALUE : ChronoUnit.DAYS.between(LocalDate.now(), sunsetAt);

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("id", text(source.get("id")));
        normalized.put("status", status);
        normalized.put("legacyPath", legacyPath);
        normalized.put("replacementPath", text(source.get("replacementPath")));
        normalized.put("announcedAt", announcedAt == null ? null : announcedAt.toString());
        normalized.put("sunsetAt", sunsetAt == null ? null : sunsetAt.toString());
        normalized.put("sunsetBaseline", text(source.get("sunsetBaseline")));
        normalized.put("owner", text(source.get("owner")));
        normalized.put("migrationDoc", migrationDoc);
        normalized.put("noticeWindowDays", noticeWindowDays);
        normalized.put("meetsNoticeWindow", meetsNoticeWindow);
        normalized.put("daysToSunset", daysToSunset == Long.MIN_VALUE ? null : daysToSunset);
        normalized.put("notes", text(source.get("notes")));
        normalized.put("valid", issues.isEmpty());
        normalized.put("issues", issues);
        if (!issues.isEmpty()) {
            log.warn("Deprecation registry entry has issues. id={}, issues={}", normalized.get("id"), issues);
        }
        return normalized;
    }

    private LocalDate parseDate(String text, String field, List<String> issues) {
        if (StringUtils.isBlank(text)) {
            issues.add(field + " is required");
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception ex) {
            issues.add(field + " format invalid: " + text);
            return null;
        }
    }

    private String normalizeStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return "ANNOUNCED";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
