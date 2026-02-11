package com.getoffer.test;

import com.getoffer.api.response.Response;
import com.getoffer.trigger.http.GlobalApiExceptionHandler;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class GlobalApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    public void shouldHandleAppException() throws Exception {
        mockMvc.perform(get("/api/test/app-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.ILLEGAL_PARAMETER.getCode()))
                .andExpect(jsonPath("$.info").value("参数错误"));
    }

    @Test
    public void shouldHandleUnknownException() throws Exception {
        mockMvc.perform(get("/api/test/runtime-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.UN_ERROR.getCode()))
                .andExpect(jsonPath("$.info").value(ResponseCode.UN_ERROR.getInfo()));
    }

    @Test
    public void shouldHandleTypeMismatchExceptionAsIllegalParameter() throws Exception {
        mockMvc.perform(get("/api/test/type-error/not-number"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.ILLEGAL_PARAMETER.getCode()));
    }

    @RestController
    private static class ErrorController {

        @GetMapping("/api/test/app-error")
        public Response<Void> appError() {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "参数错误");
        }

        @GetMapping("/api/test/runtime-error")
        public Response<Void> runtimeError() {
            throw new RuntimeException("boom");
        }

        @GetMapping("/api/test/type-error/{id}")
        public Response<String> typeError(@PathVariable("id") Long id) {
            return Response.<String>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(String.valueOf(id))
                    .build();
        }
    }
}
