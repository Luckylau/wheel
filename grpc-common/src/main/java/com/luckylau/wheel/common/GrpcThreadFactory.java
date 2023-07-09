package com.luckylau.wheel.common;

import com.luckylau.wheel.common.uti.StringUtils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class GrpcThreadFactory implements ThreadFactory {

    private final AtomicInteger id = new AtomicInteger(0);

    private String name;

    public GrpcThreadFactory(String name) {
        if (!name.endsWith(StringUtils.DOT)) {
            name += StringUtils.DOT;
        }
        this.name = name;
    }

    @Override
    public Thread newThread(Runnable r) {
        String threadName = name + id.getAndIncrement();
        Thread thread = new Thread(r, threadName);
        thread.setDaemon(true);
        return thread;
    }
}

