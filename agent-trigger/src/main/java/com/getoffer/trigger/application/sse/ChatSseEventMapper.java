package com.getoffer.trigger.application.sse;

import com.getoffer.api.dto.ChatStreamEventV3DTO;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Chat SSE 语义事件映射器。
 */
@Component
public class ChatSseEventMapper {

    private final ISessionMessageRepository sessionMessageRepository;
    private final ISessionTurnRepository sessionTurnRepository;

    public ChatSseEventMapper(ISessionMessageRepository sessionMessageRepository,
                              ISessionTurnRepository sessionTurnRepository) {
        this.sessionMessageRepository = sessionMessageRepository;
        this.sessionTurnRepository = sessionTurnRepository;
    }

    public ChatStreamEventV3DTO mapTaskEvent(Long sessionId, Long planId, Long fallbackTurnId, PlanTaskEventEntity event) {
        Map<String, Object> eventData = event.getEventData() == null ? Collections.emptyMap() : event.getEventData();
        ChatStreamEventV3DTO payload = new ChatStreamEventV3DTO();
        payload.setEventId(event.getId());
        payload.setSessionId(sessionId);
        payload.setPlanId(planId);
        payload.setTurnId(resolveTurnIdFromEvent(eventData, fallbackTurnId));
        payload.setTaskId(toLong(eventData.get("taskId")) == null ? event.getTaskId() : toLong(eventData.get("taskId")));
        payload.setTaskStatus(StringUtils.defaultIfBlank(valueOf(eventData.get("status")), null));
        payload.setMetadata(eventData);

        PlanTaskEventTypeEnum type = event.getEventType();
        if (type == PlanTaskEventTypeEnum.TASK_STARTED) {
            payload.setType("task.progress");
            payload.setMessage("任务开始：" + StringUtils.defaultIfBlank(valueOf(eventData.get("nodeId")), "unknown"));
            return payload;
        }
        if (type == PlanTaskEventTypeEnum.TASK_COMPLETED) {
            payload.setType("task.completed");
            payload.setMessage("任务结束：" + StringUtils.defaultIfBlank(valueOf(eventData.get("status")), "UNKNOWN"));
            return payload;
        }
        payload.setType("task.progress");
        payload.setMessage(resolveTaskLogMessage(eventData));
        return payload;
    }

    public String resolveFinalAnswer(Map<String, Object> eventData, Long fallbackTurnId) {
        Long assistantMessageId = toLong(eventData.get("assistantMessageId"));
        if (assistantMessageId != null) {
            SessionMessageEntity message = sessionMessageRepository.findById(assistantMessageId);
            if (message != null && StringUtils.isNotBlank(message.getContent())) {
                return message.getContent();
            }
        }

        String assistantSummary = valueOf(eventData.get("assistantSummary"));
        if (StringUtils.isNotBlank(assistantSummary)) {
            return assistantSummary;
        }

        Long turnId = resolveTurnIdFromEvent(eventData, fallbackTurnId);
        if (turnId != null) {
            SessionTurnEntity turn = sessionTurnRepository.findById(turnId);
            if (turn != null && StringUtils.isNotBlank(turn.getAssistantSummary())) {
                return turn.getAssistantSummary();
            }
        }

        return "本轮执行已结束，但未生成可展示文本。";
    }

    public Long resolveTurnIdFromEvent(Map<String, Object> eventData, Long fallbackTurnId) {
        Long turnId = toLong(eventData.get("turnId"));
        return turnId == null ? fallbackTurnId : turnId;
    }

    private String resolveTaskLogMessage(Map<String, Object> eventData) {
        String output = valueOf(eventData.get("output"));
        if (StringUtils.isNotBlank(output)) {
            return output;
        }
        String message = valueOf(eventData.get("message"));
        if (StringUtils.isNotBlank(message)) {
            return message;
        }
        String status = valueOf(eventData.get("status"));
        if (StringUtils.isNotBlank(status)) {
            return "任务状态更新：" + status;
        }
        return "任务处理中...";
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
