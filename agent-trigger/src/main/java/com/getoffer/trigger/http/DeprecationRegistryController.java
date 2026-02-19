package com.getoffer.trigger.http;

import com.getoffer.api.response.Response;
import com.getoffer.trigger.application.query.DeprecationRegistryQueryService;
import com.getoffer.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 非 V3 接口废弃治理注册表 API。
 */
@RestController
@RequestMapping("/api/governance/deprecations")
public class DeprecationRegistryController {

    private final DeprecationRegistryQueryService queryService;

    public DeprecationRegistryController(DeprecationRegistryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public Response<Map<String, Object>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "includeRemoved", required = false) Boolean includeRemoved) {
        List<Map<String, Object>> items = queryService.list(status, includeRemoved);
        Map<String, Long> summary = summarizeByStatus(items);
        Map<String, Object> policy = new HashMap<>();
        policy.put("minNoticeWindowDays", queryService.minNoticeWindowDays());

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", items.size());
        data.put("statusSummary", summary);
        data.put("policy", policy);
        data.put("generatedAt", LocalDateTime.now());
        return success(data);
    }

    private Map<String, Long> summarizeByStatus(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        return items.stream()
                .collect(Collectors.groupingBy(
                        item -> String.valueOf(item.getOrDefault("status", "UNKNOWN")),
                        Collectors.counting()
                ));
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
