package com.getoffer.api.dto;

import lombok.Data;

/**
 * 登出响应 DTO。
 */
@Data
public class AuthLogoutResponseDTO {

    private Boolean success;
    private String message;
}
