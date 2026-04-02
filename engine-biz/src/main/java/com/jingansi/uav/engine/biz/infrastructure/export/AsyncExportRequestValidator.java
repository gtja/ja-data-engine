package com.jingansi.uav.engine.biz.infrastructure.export;

/**
 * 异步导出请求校验器。
 * 具体业务把原来各自 service 里的参数校验逻辑传进来即可。
 *
 * @param <T> 导出请求类型
 */
@FunctionalInterface
public interface AsyncExportRequestValidator<T> {

    /**
     * 校验导出请求，不通过时直接抛出业务异常。
     */
    void validate(T request);
}
