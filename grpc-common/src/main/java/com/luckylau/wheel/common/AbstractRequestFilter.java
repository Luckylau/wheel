package com.luckylau.wheel.common;

import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.request.Request;
import com.luckylau.wheel.common.request.RequestMeta;
import com.luckylau.wheel.common.response.Response;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public abstract class AbstractRequestFilter {


    private RequestFilters requestFilters;

    public AbstractRequestFilter() {
    }

    @PostConstruct
    public void init() {
        requestFilters.registerFilter(this);
    }

    protected Class getResponseClazz(Class handlerClazz) throws GrpcException {
        ParameterizedType parameterizedType = (ParameterizedType) handlerClazz.getGenericSuperclass();
        try {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            return Class.forName(actualTypeArguments[1].getTypeName());

        } catch (Exception e) {
            throw new GrpcException(GrpcException.SERVER_ERROR, e);
        }
    }

    protected Method getHandleMethod(Class handlerClazz) throws GrpcException {
        try {
            Method method = handlerClazz.getMethod("handle", Request.class, RequestMeta.class);
            return method;
        } catch (NoSuchMethodException e) {
            throw new GrpcException(GrpcException.SERVER_ERROR, e);
        }
    }

    protected <T> Response getDefaultResponseInstance(Class handlerClazz) throws GrpcException {
        ParameterizedType parameterizedType = (ParameterizedType) handlerClazz.getGenericSuperclass();
        try {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            return (Response) Class.forName(actualTypeArguments[1].getTypeName()).newInstance();

        } catch (Exception e) {
            throw new GrpcException(GrpcException.SERVER_ERROR, e);
        }
    }

    /**
     * filter request.
     *
     * @param request      request.
     * @param meta         request meta.
     * @param handlerClazz request handler clazz.
     * @return response
     * @throws GrpcException GrpcException.
     */
    protected abstract Response filter(Request request, RequestMeta meta, Class handlerClazz) throws GrpcException;
}

