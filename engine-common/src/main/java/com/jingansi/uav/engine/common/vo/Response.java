package com.jingansi.uav.engine.common.vo;

import com.jingansi.uav.engine.common.bo.Result;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 接口统一返回体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String ERROR_CODE = "ERROR";

    private String code;
    private String message;
    private T data;

    public static <T> Response<T> success(T data) {
        return new Response<>(SUCCESS_CODE, "成功", data);
    }

    public static <T> Response<T> error(String message) {
        return new Response<>(ERROR_CODE, message, null);
    }

    public static <T> Response<T> returnByCode(Result<T> result) {
        if (result == null) {
            return error("服务返回为空");
        }
        return result.isSuccess() ? success(result.getData()) : error(result.getMsg());
    }
}
