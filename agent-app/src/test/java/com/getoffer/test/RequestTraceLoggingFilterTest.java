package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.api.response.Response;
import com.getoffer.config.ObservabilityHttpLogProperties;
import com.getoffer.config.RequestTraceLoggingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RequestTraceLoggingFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        ObservabilityHttpLogProperties properties = new ObservabilityHttpLogProperties();
        properties.setEnabled(true);
        properties.setLogRequestBody(false);
        properties.setSampleRate(1.0D);

        RequestTraceLoggingFilter filter = new RequestTraceLoggingFilter(new ObjectMapper(), properties);
        this.mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addFilters(filter)
                .build();
    }

    @Test
    public void shouldInjectTraceHeadersForApiRequests() throws Exception {
        mockMvc.perform(post("/api/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("0000"));
    }

    @Test
    public void shouldSkipExcludedSsePath() throws Exception {
        mockMvc.perform(get("/api/v3/chat/sessions/1/stream"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Trace-Id"))
                .andExpect(header().doesNotExist("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("0000"));
    }

    @RestController
    private static class TestController {

        @PostMapping("/api/test/echo")
        public Response<Map<String, Object>> echo(@RequestBody(required = false) Map<String, Object> request) {
            return Response.<Map<String, Object>>builder()
                    .code("0000")
                    .info("成功")
                    .data(request)
                    .build();
        }

        @GetMapping("/api/v3/chat/sessions/{id}/stream")
        public Response<String> stream(@PathVariable("id") Long planId) {
            return Response.<String>builder()
                    .code("0000")
                    .info("成功")
                    .data(String.valueOf(planId))
                    .build();
        }
    }
}
