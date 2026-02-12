package com.getoffer.trigger.http;

import com.getoffer.api.response.Response;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.IPlanTaskEventRepository;
import com.getoffer.domain.task.adapter.repository.ITaskShareLinkRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.domain.task.model.entity.TaskShareLinkEntity;
import com.getoffer.types.enums.ResponseCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务分享匿名访问 API。
 */
@RestController
@RequestMapping("/api/share/tasks")
public class ShareAccessController {

    private final IAgentTaskRepository agentTaskRepository;
    private final ITaskShareLinkRepository taskShareLinkRepository;
    private final IPlanTaskEventRepository planTaskEventRepository;

    @Value("${app.share.token-salt:agent-share-salt}")
    private String shareTokenSalt;

    public ShareAccessController(IAgentTaskRepository agentTaskRepository,
                                 ITaskShareLinkRepository taskShareLinkRepository,
                                 IPlanTaskEventRepository planTaskEventRepository) {
        this.agentTaskRepository = agentTaskRepository;
        this.taskShareLinkRepository = taskShareLinkRepository;
        this.planTaskEventRepository = planTaskEventRepository;
    }

    @GetMapping("/{id}")
    public Response<Map<String, Object>> getSharedTask(@PathVariable("id") Long taskId,
                                                        @RequestParam("code") String shareCode,
                                                        @RequestParam("token") String token) {
        if (taskId == null || taskId <= 0) {
            return illegal("任务ID不能为空");
        }
        if (StringUtils.isBlank(shareCode) || StringUtils.isBlank(token)) {
            return illegal("分享参数缺失");
        }

        AgentTaskEntity task = agentTaskRepository.findById(taskId);
        if (task == null) {
            return illegal("链接不存在或无效");
        }

        TaskShareLinkEntity link = taskShareLinkRepository.findByTaskIdAndShareCode(taskId, shareCode.trim());
        if (link == null) {
            return illegal("链接不存在或无效");
        }
        if (Boolean.TRUE.equals(link.getRevoked())) {
            return illegal("链接已撤销");
        }
        if (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(LocalDateTime.now())) {
            return illegal("链接已过期");
        }

        String incomingTokenHash = hashToken(token.trim());
        if (!safeEquals(incomingTokenHash, link.getTokenHash())) {
            return illegal("链接不存在或无效");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("taskName", task.getName());
        result.put("status", task.getStatus() == null ? "UNKNOWN" : task.getStatus().name());
        result.put("outputResult", StringUtils.defaultIfBlank(task.getOutputResult(), "任务尚未产生最终输出。"));
        result.put("references", collectReferences(task));
        result.put("scope", StringUtils.defaultIfBlank(link.getScope(), "RESULT_AND_REFERENCES"));
        result.put("shareId", link.getId());
        result.put("shareCode", link.getShareCode());
        result.put("expiresAt", link.getExpiresAt());
        result.put("sharedAt", LocalDateTime.now());
        return success(result);
    }

    private List<Map<String, Object>> collectReferences(AgentTaskEntity task) {
        List<Map<String, Object>> references = new ArrayList<>();
        Map<String, Object> inputContext = parseObject(task.getInputContext());
        Map<String, Object> configSnapshot = parseObject(task.getConfigSnapshot());

        references.addAll(normalizeReferences(inputContext == null ? null : inputContext.get("references"), "输入上下文"));
        references.addAll(normalizeReferences(configSnapshot == null ? null : configSnapshot.get("references"), "配置引用"));

        if (task.getPlanId() != null && task.getId() != null) {
            List<PlanTaskEventEntity> events = planTaskEventRepository.findByPlanIdAfterEventId(task.getPlanId(), 0L, 500);
            if (events != null) {
                for (PlanTaskEventEntity event : events) {
                    if (event == null || event.getTaskId() == null || !task.getId().equals(event.getTaskId())) {
                        continue;
                    }
                    references.addAll(normalizeReferences(event.getEventData() == null ? null : event.getEventData().get("references"), "执行引用"));
                }
            }
        }

        LinkedHashMap<String, Map<String, Object>> deduplicate = new LinkedHashMap<>();
        for (Map<String, Object> item : references) {
            String key = String.format("%s::%s",
                    String.valueOf(item.getOrDefault("type", "-")),
                    String.valueOf(item.getOrDefault("title", "-")));
            deduplicate.putIfAbsent(key, item);
            if (deduplicate.size() >= 20) {
                break;
            }
        }
        return new ArrayList<>(deduplicate.values());
    }

    private List<Map<String, Object>> normalizeReferences(Object source, String fallbackType) {
        if (source == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();

        if (source instanceof List<?> rows) {
            for (Object row : rows) {
                result.addAll(normalizeReferences(row, fallbackType));
            }
            return result;
        }

        if (source instanceof String text) {
            String title = text.trim();
            if (!title.isEmpty()) {
                Map<String, Object> item = new HashMap<>();
                item.put("title", title);
                item.put("type", fallbackType);
                result.add(item);
            }
            return result;
        }

        Map<String, Object> object = parseObject(source);
        if (object == null) {
            return result;
        }

        String title = Arrays.stream(new Object[]{object.get("title"), object.get("name"), object.get("source"), object.get("id")})
                .filter(item -> item != null)
                .map(String::valueOf)
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .findFirst()
                .orElse("");
        if (title.isEmpty()) {
            return result;
        }

        Map<String, Object> row = new HashMap<>();
        row.put("title", title);
        row.put("type", object.get("type") == null ? fallbackType : String.valueOf(object.get("type")));
        if (object.get("source") != null) {
            row.put("source", String.valueOf(object.get("source")));
        }
        Double score = parseDouble(object.get("score"));
        if (score != null) {
            row.put("score", score);
        }
        result.add(row);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return null;
        }
        return (Map<String, Object>) map;
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = token + ":" + StringUtils.defaultString(shareTokenSalt);
            byte[] hashed = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private boolean safeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private <T> Response<T> illegal(String message) {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(message)
                .build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
