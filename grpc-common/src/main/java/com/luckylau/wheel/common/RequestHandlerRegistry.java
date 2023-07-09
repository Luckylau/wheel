package com.luckylau.wheel.common;

import org.reflections.Reflections;

import javax.annotation.PostConstruct;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class RequestHandlerRegistry {
    Map<String, RequestHandler> registryHandlers = new HashMap<>();

    @PostConstruct
    public void init() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Reflections reflections = new Reflections("com.luckylau.wheel.common");
        Set<Class<? extends RequestHandler>> implementations = reflections.getSubTypesOf(RequestHandler.class);
        for (Class clazz : implementations) {
            boolean skip = false;
            while (!clazz.getSuperclass().equals(RequestHandler.class)) {
                if (clazz.getSuperclass().equals(Object.class)) {
                    skip = true;
                    break;
                }
                clazz = clazz.getSuperclass();
            }
            if (skip) {
                continue;
            }
            Class tClass = (Class) ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];
            registryHandlers.putIfAbsent(tClass.getSimpleName(), (RequestHandler) clazz.getDeclaredConstructor().newInstance());
        }

    }

    /**
     * Get Request Handler By request Type.
     *
     * @param requestType see definitions  of sub constants classes of RequestTypeConstants
     * @return request handler.
     */
    public RequestHandler getByRequestType(String requestType) {
        return registryHandlers.get(requestType);
    }
}
