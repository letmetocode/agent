package com.getoffer.trigger.http;

import com.getoffer.api.dto.SessionCreateRequestDTO;
import com.getoffer.api.dto.SessionCreateResponseDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话 API
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final IAgentSessionRepository agentSessionRepository;

    public SessionController(IAgentSessionRepository agentSessionRepository) {
        this.agentSessionRepository = agentSessionRepository;
    }

    @PostMapping
    public Response<SessionCreateResponseDTO> createSession(@RequestBody SessionCreateRequestDTO request) {
        if (request == null || StringUtils.isBlank(request.getUserId())) {
            return Response.<SessionCreateResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("UserId不能为空")
                    .build();
        }
        try {
            AgentSessionEntity entity = new AgentSessionEntity();
            entity.setUserId(request.getUserId());
            entity.setTitle(request.getTitle());
            entity.setMetaInfo(request.getMetaInfo());
            entity.activate();

            AgentSessionEntity saved = agentSessionRepository.save(entity);

            SessionCreateResponseDTO data = new SessionCreateResponseDTO();
            data.setSessionId(saved.getId());
            data.setUserId(saved.getUserId());
            data.setTitle(saved.getTitle());
            data.setActive(saved.getIsActive());

            return Response.<SessionCreateResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception ex) {
            log.warn("创建会话失败: {}", ex.getMessage());
            return Response.<SessionCreateResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }
}
