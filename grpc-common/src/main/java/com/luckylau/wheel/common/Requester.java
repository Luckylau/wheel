package com.luckylau.wheel.common;

import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.request.Request;
import com.luckylau.wheel.common.request.RequestCallBack;
import com.luckylau.wheel.common.request.RequestFuture;
import com.luckylau.wheel.common.response.Response;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public interface Requester {
    /**
     * send request.
     *
     * @param request      request.
     * @param timeoutMills mills of timeouts.
     * @return response  response returned.
     * @throws GrpcException exception throw.
     */
    Response request(Request request, long timeoutMills) throws GrpcException;

    /**
     * send request.
     *
     * @param request request.
     * @return request future.
     * @throws GrpcException exception throw.
     */
    RequestFuture requestFuture(Request request) throws GrpcException;

    /**
     * send async request.
     *
     * @param request         request.
     * @param requestCallBack callback of request.
     * @throws GrpcException exception throw.
     */
    void asyncRequest(Request request, RequestCallBack requestCallBack) throws GrpcException;

    /**
     * close connection.
     */
    void close();
}
