package com.jingansi.uav.engine.biz.infrastructure.export;

import com.jingansi.uav.engine.dao.entity.AsyncExportTask;

/**
 * 异步导出任务处理器。
 * 具体业务把原来各自 worker 的执行逻辑传进来即可。
 *
 * @param <T> 导出请求类型
 */
@FunctionalInterface
public interface AsyncExportTaskProcessor<T> {

    /**
     * 接收已创建的导出任务和原始请求，触发异步导出。
     */
    void process(AsyncExportTask task, T request);
}
