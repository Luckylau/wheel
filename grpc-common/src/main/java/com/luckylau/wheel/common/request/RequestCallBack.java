package com.luckylau.wheel.common.request;

import com.luckylau.wheel.common.response.Response;

import java.util.concurrent.Executor;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public interface RequestCallBack<T extends Response> {
    /**
     * get executor on callback.
     *
     * @return executor.
     */
    Executor getExecutor();

    /**
     * get timeout mills.
     *
     * @return timeouts.
     */
    long getTimeout();

    /**
     * called on success.
     *
     * @param response response received.
     */
    void onResponse(T response);

    /**
     * called on failed.
     *
     * @param e exception throwed.
     */
    void onException(Throwable e);
}
