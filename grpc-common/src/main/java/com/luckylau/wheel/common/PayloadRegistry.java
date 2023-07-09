package com.luckylau.wheel.common;

import com.luckylau.wheel.common.request.Request;
import com.luckylau.wheel.common.response.Response;
import org.reflections.Reflections;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class PayloadRegistry {
    private static final Map<String, Class> REGISTRY_REQUEST = new HashMap<String, Class>();

    static boolean initialized = false;

    public static void init() {
        scan();
    }

    private static synchronized void scan() {
        if (initialized) {
            return;
        }

        List<String> requestScanPackage = Arrays
                .asList("com.luckylau.wheel.common.request");
        for (String pkg : requestScanPackage) {
            Reflections reflections = new Reflections(pkg);
            Set<Class<? extends Request>> subTypesRequest = reflections.getSubTypesOf(Request.class);
            for (Class clazz : subTypesRequest) {
                register(clazz.getSimpleName(), clazz);
            }
        }
        List<String> responseScanPackage = Arrays
                .asList("com.luckylau.wheel.common.response");
        for (String pkg : responseScanPackage) {
            Reflections reflections = new Reflections(pkg);
            Set<Class<? extends Response>> subTypesOfResponse = reflections.getSubTypesOf(Response.class);
            for (Class clazz : subTypesOfResponse) {
                register(clazz.getSimpleName(), clazz);
            }
        }

        initialized = true;
    }

    static void register(String type, Class clazz) {
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }
        if (Modifier.isInterface(clazz.getModifiers())) {
            return;
        }
        if (REGISTRY_REQUEST.containsKey(type)) {
            throw new RuntimeException(String.format("Fail to register, type:%s ,clazz:%s ", type, clazz.getName()));
        }
        REGISTRY_REQUEST.put(type, clazz);
    }

    public static Class getClassByType(String type) {
        return REGISTRY_REQUEST.get(type);
    }

}
