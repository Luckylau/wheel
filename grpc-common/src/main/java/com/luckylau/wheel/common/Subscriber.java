package com.luckylau.wheel.common;

import com.luckylau.wheel.common.event.Event;

import java.util.concurrent.Executor;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public abstract class Subscriber<T extends Event> {

    /**
     * Event callback.
     *
     * @param event {@link Event}
     */
    public abstract void onEvent(T event);

    /**
     * Type of this subscriber's subscription.
     *
     * @return Class which extends {@link Event}
     */
    public abstract Class<? extends Event> subscribeType();

    /**
     * It is up to the listener to determine whether the callback is asynchronous or synchronous.
     *
     * @return {@link Executor}
     */
    public Executor executor() {
        return null;
    }

    /**
     * Whether to ignore expired events.
     *
     * @return default value is {@link Boolean#FALSE}
     */
    public boolean ignoreExpireEvent() {
        return false;
    }
}

