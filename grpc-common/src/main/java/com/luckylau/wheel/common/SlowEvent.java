package com.luckylau.wheel.common;

import com.luckylau.wheel.common.event.Event;

public abstract class SlowEvent extends Event {

    @Override
    public long sequence() {
        return 0;
    }
}