package com.luckylau.wheel.grpc.server;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class PushAckIdGenerator {

    private static final int ID_PREV_REGEN_OFFSET = 1000;
    private static AtomicLong id = new AtomicLong(0L);

    /**
     * get server push id.
     */
    public static long getNextId() {
        if (id.longValue() > Long.MAX_VALUE - ID_PREV_REGEN_OFFSET) {
            id.getAndSet(0L);
        }
        return id.incrementAndGet();
    }

}