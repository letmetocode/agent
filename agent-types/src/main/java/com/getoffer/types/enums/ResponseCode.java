package com.getoffer.types.enums;

import lombok.Getter;

@Getter
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),
    ;

    private final String code;
    private final String info;

    ResponseCode(String code, String info) {
        this.code = code;
        this.info = info;
    }

}
