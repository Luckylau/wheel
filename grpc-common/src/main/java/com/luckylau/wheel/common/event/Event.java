package com.luckylau.wheel.common.event;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Event implements Serializable {

    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private final long sequence = SEQUENCE.getAndIncrement();

    /**
     * Event sequence number, which can be used to handle the sequence of events.
     *
     * @return sequence num, It's best to make sure it's monotone.
     */
    public long sequence() {
        return sequence;
    }

}