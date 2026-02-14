package com.getoffer.test;

import com.getoffer.trigger.application.command.AuthSessionCommandService;
import com.getoffer.trigger.http.AuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        AuthSessionCommandService authSessionCommandService =
                new AuthSessionCommandService("admin", "admin123", "Operator", 24);
        this.mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authSessionCommandService)).build();
    }

    @Test
    public void shouldLoginWithValidCredential() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.userId").value("admin"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    public void shouldRejectInvalidCredential() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"));
    }

    @Test
    public void shouldReturnIllegalForMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"));
    }
}
