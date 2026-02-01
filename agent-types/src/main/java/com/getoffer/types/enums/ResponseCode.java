package com.getoffer.types.enums;

import lombok.Getter;

/**
 * 统一响应码枚举。
 * <p>
 * 定义系统中所有API响应的响应码和对应描述信息。
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Getter
public enum ResponseCode {

    /** 成功 */
    SUCCESS("0000", "成功"),

    /** 未知错误 */
    UN_ERROR("0001", "未知失败"),

    /** 非法参数 */
    ILLEGAL_PARAMETER("0002", "非法参数");

    private final String code;
    private final String info;

    ResponseCode(String code, String info) {
        this.code = code;
        this.info = info;
    }

}
