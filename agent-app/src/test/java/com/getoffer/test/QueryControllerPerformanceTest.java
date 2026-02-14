package com.getoffer.test;

import com.getoffer.domain.agent.adapter.repository.IAgentToolCatalogRepository;
import com.getoffer.domain.agent.adapter.repository.IVectorStoreRegistryRepository;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.IPlanTaskEventRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.trigger.application.common.TaskDetailViewAssembler;
import com.getoffer.trigger.http.QueryController;
import com.getoffer.types.enums.TaskStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class QueryControllerPerformanceTest {

    private MockMvc mockMvc;
    private IAgentSessionRepository agentSessionRepository;
    private IAgentPlanRepository agentPlanRepository;
    private IAgentTaskRepository agentTaskRepository;
    private ITaskExecutionRepository taskExecutionRepository;
    private IPlanTaskEventRepository planTaskEventRepository;
    private IAgentToolCatalogRepository agentToolCatalogRepository;
    private IVectorStoreRegistryRepository vectorStoreRegistryRepository;

    @BeforeEach
    public void setUp() {
        this.agentSessionRepository = mock(IAgentSessionRepository.class);
        this.agentPlanRepository = mock(IAgentPlanRepository.class);
        this.agentTaskRepository = mock(IAgentTaskRepository.class);
        this.taskExecutionRepository = mock(ITaskExecutionRepository.class);
        this.planTaskEventRepository = mock(IPlanTaskEventRepository.class);
        this.agentToolCatalogRepository = mock(IAgentToolCatalogRepository.class);
        this.vectorStoreRegistryRepository = mock(IVectorStoreRegistryRepository.class);
        TaskDetailViewAssembler taskDetailViewAssembler = new TaskDetailViewAssembler(taskExecutionRepository);

        this.mockMvc = MockMvcBuilders.standaloneSetup(
                new QueryController(
                        agentSessionRepository,
                        agentPlanRepository,
                        agentTaskRepository,
                        taskExecutionRepository,
                        planTaskEventRepository,
                        agentToolCatalogRepository,
                        vectorStoreRegistryRepository,
                        taskDetailViewAssembler
                )
        ).build();
    }

    @Test
    public void shouldBatchResolveLatestExecutionTimeWhenListingPlanTasks() throws Exception {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(11L);

        AgentTaskEntity task1 = new AgentTaskEntity();
        task1.setId(101L);
        task1.setPlanId(11L);
        task1.setName("task-1");
        task1.setStatus(TaskStatusEnum.COMPLETED);
        task1.setUpdatedAt(LocalDateTime.of(2026, 2, 12, 10, 0, 0));

        AgentTaskEntity task2 = new AgentTaskEntity();
        task2.setId(102L);
        task2.setPlanId(11L);
        task2.setName("task-2");
        task2.setStatus(TaskStatusEnum.FAILED);
        task2.setUpdatedAt(LocalDateTime.of(2026, 2, 12, 11, 0, 0));

        when(agentPlanRepository.findById(11L)).thenReturn(plan);
        when(agentTaskRepository.findByPlanId(11L)).thenReturn(Arrays.asList(task1, task2));
        when(taskExecutionRepository.findLatestExecutionTimeByTaskIds(anyList()))
                .thenReturn(Map.of(101L, 180L, 102L, 260L));

        mockMvc.perform(get("/api/plans/11/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data[0].taskId").value(101L))
                .andExpect(jsonPath("$.data[0].latestExecutionTimeMs").value(180L))
                .andExpect(jsonPath("$.data[1].taskId").value(102L))
                .andExpect(jsonPath("$.data[1].latestExecutionTimeMs").value(260L));

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskExecutionRepository, times(1)).findLatestExecutionTimeByTaskIds(captor.capture());
        List<Long> queriedTaskIds = captor.getValue();
        assertEquals(2, queriedTaskIds.size());
        assertTrue(queriedTaskIds.contains(101L));
        assertTrue(queriedTaskIds.contains(102L));

        verify(taskExecutionRepository, never()).getMaxAttemptNumber(anyLong());
        verify(taskExecutionRepository, never()).findByTaskIdAndAttempt(anyLong(), anyInt());
    }
}
