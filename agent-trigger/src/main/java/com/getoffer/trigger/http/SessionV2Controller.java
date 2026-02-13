package com.getoffer.trigger.http;

import com.getoffer.api.dto.SessionStartRequestDTO;
import com.getoffer.api.dto.SessionStartResponseDTO;
import com.getoffer.api.response.Response;
import com.getoffer.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话启动 API（V2，兼容入口，已下线）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/sessions")
public class SessionV2Controller {

    @PostMapping
    public Response<SessionStartResponseDTO> startSession(@RequestBody(required = false) SessionStartRequestDTO request) {
        log.warn("SESSION_V2_REJECTED userId={}, agentKey={}, info={}",
                request == null ? null : request.getUserId(),
                request == null ? null : request.getAgentKey(),
                "V2 会话入口已下线，请使用 /api/v3/chat/messages");
        return Response.<SessionStartResponseDTO>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info("V2 会话入口已下线，请使用 /api/v3/chat/messages")
                .build();
    }
}
