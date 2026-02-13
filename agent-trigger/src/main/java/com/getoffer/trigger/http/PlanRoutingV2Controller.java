package com.getoffer.trigger.http;

import com.getoffer.api.dto.RoutingDecisionDTO;
import com.getoffer.api.response.Response;
import com.getoffer.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan 路由决策查询 API（V2，兼容入口，已下线）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/plans")
public class PlanRoutingV2Controller {

    @GetMapping("/{id}/routing")
    public Response<RoutingDecisionDTO> getPlanRouting(@PathVariable("id") Long planId) {
        log.warn("PLAN_ROUTING_V2_REJECTED planId={}, info={}",
                planId,
                "V2 路由入口已下线，请使用 /api/v3/chat/plans/{id}/routing");
        return Response.<RoutingDecisionDTO>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info("V2 路由入口已下线，请使用 /api/v3/chat/plans/{id}/routing")
                .build();
    }
}
