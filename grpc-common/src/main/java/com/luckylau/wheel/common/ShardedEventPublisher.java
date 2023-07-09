package com.luckylau.wheel.common;

import com.luckylau.wheel.common.event.Event;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public interface ShardedEventPublisher {
    /**
     * Add listener for default share publisher.
     *
     * @param subscriber    {@link Subscriber}
     * @param subscribeType subscribe event type, such as slow event or general event.
     */
    void addSubscriber(Subscriber subscriber, Class<? extends Event> subscribeType);

    /**
     * Remove listener for default share publisher.
     *
     * @param subscriber    {@link Subscriber}
     * @param subscribeType subscribe event type, such as slow event or general event.
     */
    void removeSubscriber(Subscriber subscriber, Class<? extends Event> subscribeType);
}
