package com.getoffer.trigger.http;

import com.getoffer.api.dto.AuthLoginRequestDTO;
import com.getoffer.api.dto.AuthLoginResponseDTO;
import com.getoffer.api.dto.AuthLogoutResponseDTO;
import com.getoffer.api.dto.AuthMeResponseDTO;
import com.getoffer.api.response.Response;
import com.getoffer.trigger.application.command.AuthSessionCommandService;
import com.getoffer.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与身份会话 API。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthSessionCommandService authSessionCommandService;

    public AuthController(AuthSessionCommandService authSessionCommandService) {
        this.authSessionCommandService = authSessionCommandService;
    }

    @PostMapping("/login")
    public Response<AuthLoginResponseDTO> login(@RequestBody AuthLoginRequestDTO request) {
        try {
            return success(authSessionCommandService.login(request));
        } catch (IllegalArgumentException ex) {
            return illegal(ex.getMessage());
        }
    }

    @PostMapping("/logout")
    public Response<AuthLogoutResponseDTO> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return success(authSessionCommandService.logout(authorization));
    }

    @GetMapping("/me")
    public Response<AuthMeResponseDTO> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            return success(authSessionCommandService.me(authorization));
        } catch (IllegalArgumentException ex) {
            return illegal(ex.getMessage());
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> illegal(String message) {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(message)
                .build();
    }
}
