package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.api.dto.AuthLoginRequestDTO;
import com.getoffer.api.response.Response;
import com.getoffer.config.ApiAuthFilter;
import com.getoffer.trigger.application.command.AuthSessionCommandService;
import com.getoffer.trigger.http.AuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ApiAuthFilterTest {

    private MockMvc mockMvc;
    private AuthSessionCommandService authSessionCommandService;

    @BeforeEach
    public void setUp() {
        this.authSessionCommandService = new AuthSessionCommandService("admin", "admin123", "Operator", 24);
        ApiAuthFilter apiAuthFilter = new ApiAuthFilter(new ObjectMapper(), authSessionCommandService);
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authSessionCommandService), new ProtectedApiController())
                .addFilters(apiAuthFilter)
                .build();
    }

    @Test
    public void shouldRejectProtectedApiWhenTokenMissing() throws Exception {
        mockMvc.perform(get("/api/protected/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("0002"));
    }

    @Test
    public void shouldAllowProtectedApiWithBearerToken() throws Exception {
        String token = loginAndGetToken();
        mockMvc.perform(get("/api/protected/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    public void shouldAllowSseEndpointWithAccessTokenQuery() throws Exception {
        String token = loginAndGetToken();
        mockMvc.perform(get("/api/v3/chat/sessions/1001/stream")
                        .param("accessToken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));
    }

    @Test
    public void shouldBypassWhitelistWithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        mockMvc.perform(get("/api/share/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));
    }

    private String loginAndGetToken() {
        AuthLoginRequestDTO request = new AuthLoginRequestDTO();
        request.setUsername("admin");
        request.setPassword("admin123");
        return authSessionCommandService.login(request).getToken();
    }

    @RestController
    private static class ProtectedApiController {

        @GetMapping("/api/protected/ping")
        public Response<String> ping() {
            return Response.<String>builder()
                    .code("0000")
                    .info("成功")
                    .data("pong")
                    .build();
        }

        @GetMapping("/api/v3/chat/sessions/{sessionId}/stream")
        public Response<String> stream(@PathVariable("sessionId") Long sessionId) {
            return Response.<String>builder()
                    .code("0000")
                    .info("成功")
                    .data(String.valueOf(sessionId))
                    .build();
        }

        @GetMapping("/api/share/tasks/{taskId}")
        public Response<String> sharedTask(@PathVariable("taskId") Long taskId) {
            return Response.<String>builder()
                    .code("0000")
                    .info("成功")
                    .data(String.valueOf(taskId))
                    .build();
        }
    }
}
