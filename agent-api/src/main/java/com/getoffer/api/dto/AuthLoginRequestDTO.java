package com.getoffer.api.dto;

import lombok.Data;

/**
 * 登录请求 DTO。
 */
@Data
public class AuthLoginRequestDTO {

    private String username;
    private String password;
}
