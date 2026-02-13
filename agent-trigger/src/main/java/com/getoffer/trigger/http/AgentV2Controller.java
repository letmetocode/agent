package com.getoffer.trigger.http;

import com.getoffer.api.dto.AgentCreateRequestDTO;
import com.getoffer.api.dto.AgentCreateResponseDTO;
import com.getoffer.api.dto.AgentSummaryDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.types.enums.ResponseCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 管理 API（V2）。
 */
@RestController
@RequestMapping("/api/v2/agents")
public class AgentV2Controller {

    private final IAgentRegistryRepository agentRegistryRepository;

    public AgentV2Controller(IAgentRegistryRepository agentRegistryRepository) {
        this.agentRegistryRepository = agentRegistryRepository;
    }

    @GetMapping("/active")
    public Response<List<AgentSummaryDTO>> listActiveAgents() {
        List<AgentRegistryEntity> agents = agentRegistryRepository.findByActive(true);
        List<AgentSummaryDTO> data = (agents == null ? Collections.<AgentRegistryEntity>emptyList() : agents).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return success(data);
    }

    @PostMapping
    public Response<AgentCreateResponseDTO> createAgent(@RequestBody AgentCreateRequestDTO request) {
        if (request == null) {
            return illegal("请求体不能为空");
        }
        if (StringUtils.isBlank(request.getName())) {
            return illegal("Agent 名称不能为空");
        }
        if (StringUtils.isBlank(request.getModelProvider())) {
            return illegal("modelProvider 不能为空");
        }
        if (StringUtils.isBlank(request.getModelName())) {
            return illegal("modelName 不能为空");
        }

        String agentKey = resolveAgentKey(request.getAgentKey(), request.getName());
        if (StringUtils.isBlank(agentKey)) {
            return illegal("agentKey 非法");
        }
        if (agentRegistryRepository.findByKey(agentKey) != null) {
            return illegal("agentKey 已存在: " + agentKey);
        }

        AgentRegistryEntity entity = new AgentRegistryEntity();
        entity.setKey(agentKey);
        entity.setName(request.getName().trim());
        entity.setModelProvider(request.getModelProvider().trim());
        entity.setModelName(request.getModelName().trim());
        entity.setBaseSystemPrompt(StringUtils.defaultIfBlank(request.getBaseSystemPrompt(), "你是一个可执行任务规划与执行的 Agent。"));
        entity.setModelOptions(request.getModelOptions() == null ? Collections.emptyMap() : request.getModelOptions());
        entity.setAdvisorConfig(request.getAdvisorConfig() == null ? Collections.emptyMap() : request.getAdvisorConfig());
        entity.setIsActive(request.getActive() == null ? Boolean.TRUE : request.getActive());

        AgentRegistryEntity saved = agentRegistryRepository.save(entity);
        AgentCreateResponseDTO data = new AgentCreateResponseDTO();
        data.setAgentId(saved.getId());
        data.setAgentKey(saved.getKey());
        data.setName(saved.getName());
        data.setModelProvider(saved.getModelProvider());
        data.setModelName(saved.getModelName());
        data.setActive(saved.getIsActive());
        data.setCreatedAt(saved.getCreatedAt());
        return success(data);
    }

    private AgentSummaryDTO toSummary(AgentRegistryEntity entity) {
        AgentSummaryDTO dto = new AgentSummaryDTO();
        dto.setAgentId(entity.getId());
        dto.setAgentKey(entity.getKey());
        dto.setName(entity.getName());
        dto.setModelProvider(entity.getModelProvider());
        dto.setModelName(entity.getModelName());
        dto.setActive(entity.getIsActive());
        return dto;
    }

    private String resolveAgentKey(String requestKey, String name) {
        String source = StringUtils.defaultIfBlank(requestKey, name);
        String normalized = source.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (StringUtils.isBlank(normalized)) {
            return null;
        }
        return normalized;
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> illegal(String message) {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(message)
                .build();
    }
}
