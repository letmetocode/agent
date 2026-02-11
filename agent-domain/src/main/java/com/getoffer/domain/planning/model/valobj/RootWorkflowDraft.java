package com.getoffer.domain.planning.model.valobj;

import lombok.Data;

import java.util.Map;

/**
 * Root 规划输出的候选 Workflow Draft。
 */
@Data
public class RootWorkflowDraft {

    /**
     * 分类。
     */
    private String category;

    /**
     * 模板名称。
     */
    private String name;

    /**
     * 路由描述。
     */
    private String routeDescription;

    /**
     * 图定义。
     */
    private Map<String, Object> graphDefinition;

    /**
     * 输入模式。
     */
    private Map<String, Object> inputSchema;

    /**
     * 默认配置。
     */
    private Map<String, Object> defaultConfig;

    /**
     * 工具策略。
     */
    private Map<String, Object> toolPolicy;

    /**
     * 约束定义。
     */
    private Map<String, Object> constraints;

    /**
     * 输入 schema 版本。
     */
    private String inputSchemaVersion;

    /**
     * 节点签名。
     */
    private String nodeSignature;
}
