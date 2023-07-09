package com.luckylau.wheel.common;

import com.luckylau.wheel.common.event.Event;

import java.util.List;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public abstract class SmartSubscriber extends Subscriber {

    /**
     * Returns which event type are smartsubscriber interested in.
     *
     * @return The interestd event types.
     */
    public abstract List<Class<? extends Event>> subscribeTypes();

    @Override
    public final Class<? extends Event> subscribeType() {
        return null;
    }

    @Override
    public final boolean ignoreExpireEvent() {
        return false;
    }
}
