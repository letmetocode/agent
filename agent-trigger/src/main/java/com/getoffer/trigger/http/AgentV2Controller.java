package com.getoffer.trigger.http;

import com.getoffer.api.dto.AgentCreateRequestDTO;
import com.getoffer.api.dto.AgentCreateResponseDTO;
import com.getoffer.api.dto.AgentSummaryDTO;
import com.getoffer.api.response.Response;
import com.getoffer.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 管理 API（V2，兼容入口，已下线）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/agents")
public class AgentV2Controller {

    @GetMapping("/active")
    public Response<List<AgentSummaryDTO>> listActiveAgents() {
        log.warn("AGENT_V2_ACTIVE_REJECTED info={}", "V2 Agent 入口已下线，请使用 /api/v3/chat/messages");
        return Response.<List<AgentSummaryDTO>>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info("V2 Agent 入口已下线，请使用 /api/v3/chat/messages")
                .build();
    }

    @PostMapping
    public Response<AgentCreateResponseDTO> createAgent(@RequestBody(required = false) AgentCreateRequestDTO request) {
        log.warn("AGENT_V2_CREATE_REJECTED name={}, modelProvider={}, info={}",
                request == null ? null : request.getName(),
                request == null ? null : request.getModelProvider(),
                "V2 Agent 入口已下线，请使用 /api/v3/chat/messages");
        return Response.<AgentCreateResponseDTO>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info("V2 Agent 入口已下线，请使用 /api/v3/chat/messages")
                .build();
    }
}
