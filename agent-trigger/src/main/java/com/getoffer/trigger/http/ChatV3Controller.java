package com.getoffer.trigger.http;

import com.getoffer.api.dto.ChatHistoryResponseV3DTO;
import com.getoffer.api.dto.ChatMessageSubmitRequestV3DTO;
import com.getoffer.api.dto.ChatMessageSubmitResponseV3DTO;
import com.getoffer.api.response.Response;
import com.getoffer.trigger.application.command.ChatConversationCommandService;
import com.getoffer.trigger.application.query.ChatHistoryQueryService;
import com.getoffer.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V3 Chat API：提供会话编排聚合能力。
 */
@RestController
@RequestMapping("/api/v3/chat")
public class ChatV3Controller {

    private final ChatConversationCommandService chatConversationCommandService;
    private final ChatHistoryQueryService chatHistoryQueryService;

    public ChatV3Controller(ChatConversationCommandService chatConversationCommandService,
                            ChatHistoryQueryService chatHistoryQueryService) {
        this.chatConversationCommandService = chatConversationCommandService;
        this.chatHistoryQueryService = chatHistoryQueryService;
    }

    @PostMapping("/messages")
    public Response<ChatMessageSubmitResponseV3DTO> submitMessage(@RequestBody ChatMessageSubmitRequestV3DTO request) {
        ChatConversationCommandService.ConversationSubmitResult result = chatConversationCommandService.submitMessage(request);

        ChatMessageSubmitResponseV3DTO data = new ChatMessageSubmitResponseV3DTO();
        data.setSessionId(result.getSessionId());
        data.setTurnId(result.getTurnId());
        data.setPlanId(result.getPlanId());
        data.setTurnStatus(result.getTurnStatus());
        data.setAccepted(result.getAccepted());
        data.setSubmissionState(result.getSubmissionState());
        data.setAcceptedAt(result.getAcceptedAt());
        data.setSessionTitle(result.getSessionTitle());
        data.setAssistantMessage(result.getAssistantMessage());
        data.setRoutingDecision(result.getRoutingDecision());
        if (result.getPlanId() != null) {
            data.setStreamPath(String.format("/api/v3/chat/sessions/%d/stream?planId=%d", result.getSessionId(), result.getPlanId()));
        }
        data.setHistoryPath(String.format("/api/v3/chat/sessions/%d/history", result.getSessionId()));

        return success(data);
    }

    @GetMapping("/sessions/{id}/history")
    public Response<ChatHistoryResponseV3DTO> getHistory(@PathVariable("id") Long sessionId) {
        return success(chatHistoryQueryService.getHistory(sessionId));
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
