package com.luckylau.wheel.common;

import com.luckylau.wheel.common.request.Request;
import com.luckylau.wheel.common.response.Response;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public interface ServerRequestHandler {

    /**
     * Handle request from server.
     *
     * @param request request
     * @return response.
     */
    Response requestReply(Request request);
}
