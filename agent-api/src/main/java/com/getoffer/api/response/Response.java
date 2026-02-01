package com.getoffer.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结果封装类。
 * <p>
 * 封装所有API接口的响应结果，包含响应码、响应描述和响应数据。
 * 支持泛型，可适配不同业务场景的返回数据类型。
 * </p>
 *
 * @param <T> 响应数据的类型
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> implements Serializable {

    private static final long serialVersionUID = 7000723935764546321L;

    /** 响应码，成功为"0000" */
    private String code;

    /** 响应描述信息 */
    private String info;

    /** 响应数据，泛型类型 */
    private T data;

}
