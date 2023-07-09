package com.luckylau.wheel.common.request;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ClientDetectionRequest extends ServerRequest {

    private static final String MODULE = "internal";

    @Override
    public String getModule() {
        return MODULE;
    }

}