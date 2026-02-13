package com.getoffer.trigger.http;

import com.getoffer.api.dto.TurnCreateRequestDTO;
import com.getoffer.api.dto.TurnCreateResponseDTO;
import com.getoffer.api.response.Response;
import com.getoffer.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回合创建 API（V2，兼容入口，已下线）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/sessions")
public class TurnV2Controller {

    @PostMapping("/{id}/turns")
    public Response<TurnCreateResponseDTO> createTurn(@PathVariable("id") Long sessionId,
                                                      @RequestBody(required = false) TurnCreateRequestDTO request) {
        log.warn("TURN_V2_REJECTED sessionId={}, messageLength={}, info={}",
                sessionId,
                request == null || request.getMessage() == null ? 0 : request.getMessage().length(),
                "V2 回合入口已下线，请使用 /api/v3/chat/messages");
        return Response.<TurnCreateResponseDTO>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info("V2 回合入口已下线，请使用 /api/v3/chat/messages")
                .build();
    }
}
