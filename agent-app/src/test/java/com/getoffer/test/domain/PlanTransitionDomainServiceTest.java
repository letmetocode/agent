package com.getoffer.test.domain;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.service.PlanTransitionDomainService;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.types.enums.PlanStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlanTransitionDomainServiceTest {

    private final PlanTransitionDomainService service = new PlanTransitionDomainService();

    @Test
    public void shouldResolveRunningWhenRunningLikeExists() {
        PlanTaskStatusStat stat = new PlanTaskStatusStat(1L, 3L, 0L, 1L, 0L);
        PlanTransitionDomainService.PlanAggregateStatus aggregateStatus = service.resolveAggregateStatus(stat);

        Assertions.assertEquals(PlanTransitionDomainService.PlanAggregateStatus.RUNNING, aggregateStatus);
        Assertions.assertEquals(PlanStatusEnum.RUNNING,
                service.resolveTargetStatus(PlanStatusEnum.READY, aggregateStatus));
    }

    @Test
    public void shouldResolveFailedWhenFailedCountExists() {
        PlanTaskStatusStat stat = new PlanTaskStatusStat(1L, 3L, 1L, 0L, 0L);
        PlanTransitionDomainService.PlanAggregateStatus aggregateStatus = service.resolveAggregateStatus(stat);

        Assertions.assertEquals(PlanTransitionDomainService.PlanAggregateStatus.FAILED, aggregateStatus);
        Assertions.assertEquals(PlanStatusEnum.FAILED,
                service.resolveTargetStatus(PlanStatusEnum.RUNNING, aggregateStatus));
    }

    @Test
    public void shouldTransitPlanToCompleted() {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setStatus(PlanStatusEnum.READY);

        service.transitPlan(plan, PlanStatusEnum.COMPLETED);

        Assertions.assertEquals(PlanStatusEnum.COMPLETED, plan.getStatus());
        Assertions.assertNotNull(plan.getUpdatedAt());
    }
}
