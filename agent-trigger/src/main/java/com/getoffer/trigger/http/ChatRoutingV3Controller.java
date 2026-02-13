package com.getoffer.trigger.http;

import com.getoffer.api.dto.RoutingDecisionDTO;
import com.getoffer.api.response.Response;
import com.getoffer.trigger.application.query.ChatRoutingQueryService;
import com.getoffer.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V3 Chat 路由决策查询 API。
 */
@RestController
@RequestMapping("/api/v3/chat/plans")
public class ChatRoutingV3Controller {

    private final ChatRoutingQueryService chatRoutingQueryService;

    public ChatRoutingV3Controller(ChatRoutingQueryService chatRoutingQueryService) {
        this.chatRoutingQueryService = chatRoutingQueryService;
    }

    @GetMapping("/{id}/routing")
    public Response<RoutingDecisionDTO> getPlanRouting(@PathVariable("id") Long planId) {
        return success(chatRoutingQueryService.getRoutingDecision(planId));
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
