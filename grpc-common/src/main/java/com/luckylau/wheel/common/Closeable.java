package com.luckylau.wheel.common;

import com.luckylau.wheel.common.exception.GrpcException;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public interface Closeable {
    /**
     * Shutdown the Resources, such as Thread Pool.
     *
     * @throws GrpcException exception.
     */
    void shutdown() throws GrpcException;
}
