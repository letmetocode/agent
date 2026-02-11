package com.getoffer.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用自定义异常类。
 * <p>
 * 统一处理应用中的业务异常，包含异常码和异常描述信息。
 * 所有业务异常应通过此类抛出，便于上层统一捕获和处理。
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 5317680961212299217L;

    /** 异常码 */
    private String code;

    /** 异常信息 */
    private String info;

    /**
     * 创建包含异常码的 AppException。
     *
     * @param code 异常码
     */
    public AppException(String code) {
        super(code);
        this.code = code;
        this.info = code;
    }

    /**
     * 创建包含异常码和原因的 AppException。
     *
     * @param code 异常码
     * @param cause 异常原因
     */
    public AppException(String code, Throwable cause) {
        super(cause == null ? null : cause.getMessage(), cause);
        this.code = code;
        this.info = cause == null ? null : cause.getMessage();
    }

    /**
     * 创建包含异常码和描述信息的 AppException。
     *
     * @param code 异常码
     * @param message 异常描述信息
     */
    public AppException(String code, String message) {
        super(message);
        this.code = code;
        this.info = message;
    }

    /**
     * 创建包含异常码、描述信息和原因的 AppException。
     *
     * @param code 异常码
     * @param message 异常描述信息
     * @param cause 异常原因
     */
    public AppException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.info = message;
    }

    @Override
    public String getMessage() {
        return info != null ? info : super.getMessage();
    }

    /**
     * 将异常转换为字符串表示。
     *
     * @return 包含异常码和描述信息的字符串
     */
    @Override
    public String toString() {
        return "com.getoffer.types.exception.AppException{" +
                "code='" + code + '\'' +
                ", info='" + info + '\'' +
                '}';
    }

}
