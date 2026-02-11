package com.getoffer.trigger.service;

import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.MessageRoleEnum;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 回合结果汇总服务：将 Plan 终态聚合为 Assistant 可读消息并落库。
 */
@Slf4j
@Service
public class TurnResultService {

    private static final int SUMMARY_MAX_LENGTH = 2000;

    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final IAgentTaskRepository agentTaskRepository;

    public TurnResultService(ISessionTurnRepository sessionTurnRepository,
                             ISessionMessageRepository sessionMessageRepository,
                             IAgentTaskRepository agentTaskRepository) {
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.agentTaskRepository = agentTaskRepository;
    }

    public TurnFinalizeResult finalizeByPlan(Long planId, PlanStatusEnum planStatus) {
        if (planId == null || planStatus == null) {
            return TurnFinalizeResult.empty();
        }
        SessionTurnEntity turn = sessionTurnRepository.findByPlanId(planId);
        if (turn == null) {
            return TurnFinalizeResult.empty();
        }
        if (turn.isTerminal()) {
            return TurnFinalizeResult.of(turn.getId(), turn.getFinalResponseMessageId(), turn.getAssistantSummary(), turn.getStatus());
        }

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(planId);
        String assistantContent = buildAssistantContent(planStatus, tasks);

        SessionMessageEntity assistantMessage = new SessionMessageEntity();
        assistantMessage.setSessionId(turn.getSessionId());
        assistantMessage.setTurnId(turn.getId());
        assistantMessage.setRole(MessageRoleEnum.ASSISTANT);
        assistantMessage.setContent(assistantContent);
        SessionMessageEntity savedMessage = sessionMessageRepository.save(assistantMessage);

        turn.setFinalResponseMessageId(savedMessage.getId());
        turn.setAssistantSummary(truncate(assistantContent, SUMMARY_MAX_LENGTH));
        turn.setStatus(planStatus == PlanStatusEnum.COMPLETED ? TurnStatusEnum.COMPLETED : TurnStatusEnum.FAILED);
        turn.setCompletedAt(LocalDateTime.now());
        sessionTurnRepository.update(turn);

        log.info("TURN_FINALIZED planId={}, turnId={}, turnStatus={}, assistantMessageId={}",
                planId,
                turn.getId(),
                turn.getStatus() == null ? null : turn.getStatus().name(),
                savedMessage.getId());

        return TurnFinalizeResult.of(turn.getId(), savedMessage.getId(), turn.getAssistantSummary(), turn.getStatus());
    }

    private String buildAssistantContent(PlanStatusEnum planStatus, List<AgentTaskEntity> tasks) {
        List<AgentTaskEntity> sortedTasks = tasks == null ? Collections.emptyList() : tasks.stream()
                .filter(task -> task != null && task.getId() != null)
                .sorted(Comparator.comparing(AgentTaskEntity::getId))
                .collect(Collectors.toList());

        if (planStatus == PlanStatusEnum.COMPLETED) {
            List<String> outputs = sortedTasks.stream()
                    .filter(task -> task.getStatus() == TaskStatusEnum.COMPLETED)
                    .filter(AgentTaskEntity::isWorkerTask)
                    .map(AgentTaskEntity::getOutputResult)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
            if (outputs.isEmpty()) {
                return "本轮任务已执行完成，但暂无可展示的文本结果。";
            }
            return outputs.size() == 1
                    ? outputs.get(0)
                    : "本轮任务已完成，结果汇总如下：\n\n" + String.join("\n\n", outputs);
        }

        String failedDetail = sortedTasks.stream()
                .filter(task -> task.getStatus() == TaskStatusEnum.FAILED)
                .filter(AgentTaskEntity::isWorkerTask)
                .map(AgentTaskEntity::getOutputResult)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
        if (StringUtils.isNotBlank(failedDetail)) {
            return "本轮任务执行失败：" + failedDetail;
        }
        return "本轮任务执行失败，请稍后重试或调整输入后再发起。";
    }

    private String truncate(String text, int maxLength) {
        if (text == null || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    @Data
    public static class TurnFinalizeResult {
        private Long turnId;
        private Long assistantMessageId;
        private String assistantSummary;
        private TurnStatusEnum turnStatus;

        public static TurnFinalizeResult empty() {
            return new TurnFinalizeResult();
        }

        public static TurnFinalizeResult of(Long turnId,
                                            Long assistantMessageId,
                                            String assistantSummary,
                                            TurnStatusEnum turnStatus) {
            TurnFinalizeResult result = new TurnFinalizeResult();
            result.setTurnId(turnId);
            result.setAssistantMessageId(assistantMessageId);
            result.setAssistantSummary(assistantSummary);
            result.setTurnStatus(turnStatus);
            return result;
        }
    }
}
