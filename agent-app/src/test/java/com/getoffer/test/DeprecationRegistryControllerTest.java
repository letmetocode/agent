package com.getoffer.test;

import com.getoffer.trigger.application.query.DeprecationRegistryQueryService;
import com.getoffer.trigger.http.DeprecationRegistryController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DeprecationRegistryControllerTest {

    private MockMvc mockMvc;
    private DeprecationRegistryQueryService queryService;

    @BeforeEach
    public void setUp() {
        this.queryService = mock(DeprecationRegistryQueryService.class);
        this.mockMvc = MockMvcBuilders.standaloneSetup(new DeprecationRegistryController(queryService)).build();
    }

    @Test
    public void shouldReturnRegistrySummaryAndPolicy() throws Exception {
        when(queryService.list(null, null)).thenReturn(List.of(
                Map.of("id", "legacy-plan-query", "status", "REMOVED"),
                Map.of("id", "legacy-route", "status", "ANNOUNCED")
        ));
        when(queryService.minNoticeWindowDays()).thenReturn(30);

        mockMvc.perform(get("/api/governance/deprecations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.statusSummary.REMOVED").value(1))
                .andExpect(jsonPath("$.data.statusSummary.ANNOUNCED").value(1))
                .andExpect(jsonPath("$.data.policy.minNoticeWindowDays").value(30));

        verify(queryService, times(1)).list(null, null);
        verify(queryService, times(1)).minNoticeWindowDays();
    }

    @Test
    public void shouldPassFiltersToQueryService() throws Exception {
        when(queryService.list("removed", false)).thenReturn(List.of(
                Map.of("id", "legacy-plan-task-query", "status", "REMOVED")
        ));
        when(queryService.minNoticeWindowDays()).thenReturn(30);

        mockMvc.perform(get("/api/governance/deprecations")
                        .param("status", "removed")
                        .param("includeRemoved", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("REMOVED"));

        verify(queryService, times(1)).list("removed", false);
    }
}
