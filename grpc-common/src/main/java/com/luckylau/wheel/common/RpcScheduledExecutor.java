package com.luckylau.wheel.common;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class RpcScheduledExecutor extends ScheduledThreadPoolExecutor {
    public static final RpcScheduledExecutor TIMEOUT_SCHEDULER = new RpcScheduledExecutor(1,
            "com.luckylau.wheel.TimerScheduler");

    public static final RpcScheduledExecutor COMMON_SERVER_EXECUTOR = new RpcScheduledExecutor(1,
            "com.luckylau.wheel.ServerCommonScheduler");

    public RpcScheduledExecutor(int corePoolSize, final String threadName) {
        super(corePoolSize, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, threadName);
            }
        });
    }
}
