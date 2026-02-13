package com.getoffer.domain.planning.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plan 终态汇总领域服务：负责将任务执行结果收敛为会话终态语义。
 */
@Service
public class PlanFinalizationDomainService {

    private static final int SUMMARY_MAX_LENGTH = 2000;

    public FinalizationDecision resolveFinalization(PlanStatusEnum planStatus, List<AgentTaskEntity> tasks) {
        if (planStatus == null) {
            return FinalizationDecision.empty();
        }
        if (planStatus != PlanStatusEnum.COMPLETED && planStatus != PlanStatusEnum.FAILED) {
            return FinalizationDecision.empty();
        }

        String assistantContent = buildAssistantContent(planStatus, tasks);
        return new FinalizationDecision(
                resolveTurnStatus(planStatus),
                assistantContent,
                truncate(assistantContent, SUMMARY_MAX_LENGTH),
                true
        );
    }

    private TurnStatusEnum resolveTurnStatus(PlanStatusEnum planStatus) {
        return planStatus == PlanStatusEnum.COMPLETED ? TurnStatusEnum.COMPLETED : TurnStatusEnum.FAILED;
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
                    .filter(this::hasText)
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
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
        if (hasText(failedDetail)) {
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record FinalizationDecision(TurnStatusEnum turnStatus,
                                       String assistantContent,
                                       String assistantSummary,
                                       boolean finalizable) {

        public static FinalizationDecision empty() {
            return new FinalizationDecision(null, null, null, false);
        }
    }
}
