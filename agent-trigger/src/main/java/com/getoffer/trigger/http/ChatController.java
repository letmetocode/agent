package com.getoffer.trigger.http;

import com.getoffer.api.dto.ChatRequestDTO;
import com.getoffer.api.dto.ChatResponseDTO;
import com.getoffer.api.response.Response;
import com.getoffer.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话聊天 API（V1，已下线）。
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
public class ChatController {

    @PostMapping("/{id}/chat")
    public Response<ChatResponseDTO> chat(@PathVariable("id") Long sessionId,
                                          @RequestBody(required = false) ChatRequestDTO request) {
        String message = request == null ? null : request.getMessage();
        log.warn("CHAT_V1_REJECTED sessionId={}, messageLength={}, info={}",
                sessionId,
                message == null ? 0 : message.length(),
                "旧接口已下线，请使用 /api/v3/chat/messages");
        return Response.<ChatResponseDTO>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info("旧接口已下线，请使用 /api/v3/chat/messages")
                .build();
    }
}
