package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.trigger.http.ObservabilityAlertCatalogController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ObservabilityAlertCatalogControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        ObservabilityAlertCatalogController controller = new ObservabilityAlertCatalogController(
                new ObjectMapper(),
                new ClassPathResource("observability/alert-catalog.json")
        );
        controller.init();
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void shouldReturnAlertCatalog() throws Exception {
        mockMvc.perform(get("/api/observability/alerts/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.length()").isNumber())
                .andExpect(jsonPath("$.data[0].module").exists())
                .andExpect(jsonPath("$.data[0].alertName").exists())
                .andExpect(jsonPath("$.data[0].runbook").exists());
    }
}
