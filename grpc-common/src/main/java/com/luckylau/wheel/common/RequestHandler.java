package com.luckylau.wheel.common;

import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.request.Request;
import com.luckylau.wheel.common.request.RequestMeta;
import com.luckylau.wheel.common.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public abstract class RequestHandler<T extends Request, S extends Response> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);


    private RequestFilters requestFilters;

    /**
     * grpc的 所有请求入口Handler request.
     *
     * @param request request
     * @param meta    request meta data
     * @return response
     * @throws GrpcException nacos exception when handle request has problem.
     */
    public Response handleRequest(T request, RequestMeta meta) throws GrpcException {
        for (AbstractRequestFilter filter : requestFilters.filters) {
            try {
                Response filterResult = filter.filter(request, meta, this.getClass());
                if (filterResult != null && !filterResult.isSuccess()) {
                    return filterResult;
                }
            } catch (Throwable throwable) {
                LOGGER.error("filter error", throwable);
            }

        }
        return handle(request, meta);
    }

    /**
     * Handler request.
     *
     * @param request request
     * @param meta    request meta data
     * @return response
     * @throws GrpcException nacos exception when handle request has problem.
     */
    public abstract S handle(T request, RequestMeta meta) throws GrpcException;

}

