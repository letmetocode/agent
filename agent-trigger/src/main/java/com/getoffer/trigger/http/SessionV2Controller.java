package com.getoffer.trigger.http;

import com.getoffer.api.dto.SessionStartRequestDTO;
import com.getoffer.api.dto.SessionStartResponseDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.types.enums.ResponseCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 会话启动 API（V2）。
 */
@RestController
@RequestMapping("/api/v2/sessions")
public class SessionV2Controller {

    private final IAgentSessionRepository agentSessionRepository;
    private final IAgentRegistryRepository agentRegistryRepository;

    public SessionV2Controller(IAgentSessionRepository agentSessionRepository,
                               IAgentRegistryRepository agentRegistryRepository) {
        this.agentSessionRepository = agentSessionRepository;
        this.agentRegistryRepository = agentRegistryRepository;
    }

    @PostMapping
    public Response<SessionStartResponseDTO> startSession(@RequestBody SessionStartRequestDTO request) {
        if (request == null || StringUtils.isBlank(request.getUserId())) {
            return illegal("userId 不能为空");
        }
        if (StringUtils.isBlank(request.getAgentKey())) {
            return illegal("agentKey 不能为空");
        }

        AgentRegistryEntity agent = agentRegistryRepository.findByKey(request.getAgentKey().trim());
        if (agent == null || !Boolean.TRUE.equals(agent.getIsActive())) {
            return illegal("agentKey 不存在或未激活: " + request.getAgentKey());
        }

        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setUserId(request.getUserId().trim());
        entity.setTitle(StringUtils.trimToNull(request.getTitle()));
        entity.setAgentKey(agent.getKey());
        entity.setScenario(StringUtils.trimToNull(request.getScenario()));
        entity.setMetaInfo(mergeMetaInfo(request.getMetaInfo(), entity.getAgentKey(), entity.getScenario()));
        entity.activate();

        AgentSessionEntity saved = agentSessionRepository.save(entity);
        SessionStartResponseDTO data = new SessionStartResponseDTO();
        data.setSessionId(saved.getId());
        data.setUserId(saved.getUserId());
        data.setTitle(saved.getTitle());
        data.setAgentKey(saved.getAgentKey());
        data.setScenario(saved.getScenario());
        data.setActive(saved.getIsActive());
        data.setCreatedAt(saved.getCreatedAt());
        return success(data);
    }

    private Map<String, Object> mergeMetaInfo(Map<String, Object> requestMeta, String agentKey, String scenario) {
        Map<String, Object> merged = new HashMap<>();
        if (requestMeta != null && !requestMeta.isEmpty()) {
            merged.putAll(requestMeta);
        }
        if (StringUtils.isNotBlank(agentKey)) {
            merged.put("agentKey", agentKey);
        }
        if (StringUtils.isNotBlank(scenario)) {
            merged.put("scenario", scenario);
        }
        return merged;
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
