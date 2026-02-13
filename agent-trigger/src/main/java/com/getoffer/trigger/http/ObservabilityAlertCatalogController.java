package com.getoffer.trigger.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.api.response.Response;
import com.getoffer.types.enums.ResponseCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 观测告警目录接口：用于前端总览页提供规则与处置入口。
 */
@Slf4j
@RestController
@RequestMapping("/api/observability/alerts")
public class ObservabilityAlertCatalogController {

    private final ObjectMapper objectMapper;
    private final Resource catalogResource;
    private volatile List<Map<String, Object>> cachedCatalog = Collections.emptyList();

    public ObservabilityAlertCatalogController(ObjectMapper objectMapper,
                                               @Value("classpath:observability/alert-catalog.json") Resource catalogResource) {
        this.objectMapper = objectMapper;
        this.catalogResource = catalogResource;
    }

    @PostConstruct
    public void init() {
        this.cachedCatalog = loadCatalog();
    }

    @GetMapping("/catalog")
    public Response<List<Map<String, Object>>> getCatalog() {
        return success(cachedCatalog);
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
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception ex) {
            log.warn("Failed to load observability alert catalog. error={}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
