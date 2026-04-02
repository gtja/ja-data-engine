package com.jingansi.uav.engine.common.bo;

/**
 * 业务层统一结果。
 *
 * <p>这里保留和控制中心接近的成功/失败语义，
 * 这样 service 可以继续返回“结果 + 消息 + 数据”，
 * controller 再统一转成接口返回体。
 */
public class Result<T> {

    private boolean success;
    private String msg;
    private T data;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public static Result<Void> ok() {
        Result<Void> result = new Result<>();
        result.setSuccess(true);
        result.setMsg("OK");
        return result;
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setMsg("OK");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setMsg(message);
        return result;
    }
}
