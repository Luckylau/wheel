package com.luckylau.wheel.common;

import com.luckylau.wheel.common.event.Event;

import java.util.function.BiFunction;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public interface EventPublisherFactory extends BiFunction<Class<? extends Event>, Integer, EventPublisher> {

    /**
     * Build an new {@link EventPublisher}.
     *
     * @param eventType    eventType for {@link EventPublisher}
     * @param maxQueueSize max queue size for {@link EventPublisher}
     * @return new {@link EventPublisher}
     */
    @Override
    EventPublisher apply(Class<? extends Event> eventType, Integer maxQueueSize);
}