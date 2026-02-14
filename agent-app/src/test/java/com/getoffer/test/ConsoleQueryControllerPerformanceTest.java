package com.getoffer.test;

import com.getoffer.domain.agent.adapter.repository.IVectorStoreRegistryRepository;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.IPlanTaskEventRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.trigger.application.common.TaskDetailViewAssembler;
import com.getoffer.trigger.http.ConsoleQueryController;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConsoleQueryControllerPerformanceTest {

    private MockMvc mockMvc;
    private IAgentSessionRepository agentSessionRepository;
    private IAgentPlanRepository agentPlanRepository;
    private IAgentTaskRepository agentTaskRepository;
    private IPlanTaskEventRepository planTaskEventRepository;
    private ITaskExecutionRepository taskExecutionRepository;
    private IVectorStoreRegistryRepository vectorStoreRegistryRepository;

    @BeforeEach
    public void setUp() {
        this.agentSessionRepository = mock(IAgentSessionRepository.class);
        this.agentPlanRepository = mock(IAgentPlanRepository.class);
        this.agentTaskRepository = mock(IAgentTaskRepository.class);
        this.planTaskEventRepository = mock(IPlanTaskEventRepository.class);
        this.taskExecutionRepository = mock(ITaskExecutionRepository.class);
        this.vectorStoreRegistryRepository = mock(IVectorStoreRegistryRepository.class);
        TaskDetailViewAssembler taskDetailViewAssembler = new TaskDetailViewAssembler(taskExecutionRepository);

        this.mockMvc = MockMvcBuilders.standaloneSetup(
                new ConsoleQueryController(
                        agentSessionRepository,
                        agentPlanRepository,
                        agentTaskRepository,
                        planTaskEventRepository,
                        vectorStoreRegistryRepository,
                        taskDetailViewAssembler
                )
        ).build();
    }

    @Test
    public void shouldQueryPagedLogsFromRepositoryInsteadOfInMemoryFlatten() throws Exception {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(88L);
        plan.setUpdatedAt(LocalDateTime.of(2026, 2, 13, 10, 0, 0));
        when(agentPlanRepository.findAll()).thenReturn(List.of(plan));

        PlanTaskEventEntity event = new PlanTaskEventEntity();
        event.setId(1001L);
        event.setPlanId(88L);
        event.setTaskId(77L);
        event.setEventType(PlanTaskEventTypeEnum.TASK_COMPLETED);
        event.setEventData(Map.of("traceId", "trace-abc", "status", "FAILED"));
        event.setCreatedAt(LocalDateTime.of(2026, 2, 13, 10, 0, 1));

        when(planTaskEventRepository.countLogs(anyList(), eq(77L), eq("ERROR"), eq("trace-abc"), eq("timeout")))
                .thenReturn(1L);
        when(planTaskEventRepository.findLogsPaged(anyList(), eq(77L), eq("ERROR"), eq("trace-abc"), eq("timeout"), eq(0), eq(10)))
                .thenReturn(List.of(event));

        mockMvc.perform(get("/api/logs/paged")
                        .param("taskId", "77")
                        .param("level", "ERROR")
                        .param("traceId", "Trace-ABC")
                        .param("keyword", "timeout")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].planId").value(88))
                .andExpect(jsonPath("$.data.items[0].traceId").value("trace-abc"));

        ArgumentCaptor<List<Long>> planIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(planTaskEventRepository, times(1))
                .countLogs(planIdsCaptor.capture(), eq(77L), eq("ERROR"), eq("trace-abc"), eq("timeout"));
        List<Long> queriedPlanIds = planIdsCaptor.getValue();
        assertEquals(1, queriedPlanIds.size());
        assertTrue(queriedPlanIds.contains(88L));

        verify(planTaskEventRepository, times(1))
                .findLogsPaged(anyList(), eq(77L), eq("ERROR"), eq("trace-abc"), eq("timeout"), eq(0), eq(10));
        verify(planTaskEventRepository, never()).findByPlanIdAfterEventId(anyLong(), anyLong(), anyInt());
    }

    @Test
    public void shouldRejectIllegalLogLevel() throws Exception {
        mockMvc.perform(get("/api/logs/paged")
                        .param("level", "DEBUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"));

    }
}
