package com.getoffer.test;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.infrastructure.planning.PlannerServiceImpl;
import com.getoffer.types.enums.PlanStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Collections;

public class PlanWorkflowBoundaryTest {

    @Test
    public void shouldRequireRouteDecisionAndDefinitionSnapshotForPlanValidation() {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(1L);
        plan.setSessionId(1001L);
        plan.setPlanGoal("validation-case");
        plan.setExecutionGraph(Collections.singletonMap("nodes", Collections.emptyList()));
        plan.setDefinitionSnapshot(Collections.singletonMap("routeType", "HIT_PRODUCTION"));
        plan.setGlobalContext(Collections.emptyMap());
        plan.setStatus(PlanStatusEnum.PLANNING);

        IllegalStateException noRouteDecision = Assertions.assertThrows(IllegalStateException.class, plan::validate);
        Assertions.assertTrue(noRouteDecision.getMessage().contains("Route decision id"));

        plan.setRouteDecisionId(99L);
        plan.setDefinitionSnapshot(Collections.emptyMap());
        IllegalStateException noSnapshot = Assertions.assertThrows(IllegalStateException.class, plan::validate);
        Assertions.assertTrue(noSnapshot.getMessage().contains("Definition snapshot"));
    }

    @Test
    public void shouldKeepCreatePlanTransactionalForAtomicWrites() throws Exception {
        Method createPlan = PlannerServiceImpl.class.getMethod("createPlan", Long.class, String.class, java.util.Map.class);
        Transactional transactional = createPlan.getAnnotation(Transactional.class);
        Assertions.assertNotNull(transactional, "createPlan(sessionId, userQuery, extraContext) 必须声明事务");
    }
}

