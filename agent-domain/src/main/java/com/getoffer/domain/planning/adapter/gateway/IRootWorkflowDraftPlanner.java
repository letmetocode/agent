package com.getoffer.domain.planning.adapter.gateway;

import com.getoffer.domain.planning.model.valobj.RootWorkflowDraft;

import java.util.Map;

/**
 * Root 规划器端口：用于在未命中生产 Definition 时生成候选 Draft。
 */
public interface IRootWorkflowDraftPlanner {

    /**
     * 生成候选 Workflow Draft。
     *
     * @param sessionId 会话 ID
     * @param userQuery 用户输入
     * @param context 规划上下文
     * @return 候选草案
     */
    RootWorkflowDraft planDraft(Long sessionId, String userQuery, Map<String, Object> context);
}
