package com.luckylau.wheel.common;

import com.luckylau.wheel.common.event.Event;
import com.luckylau.wheel.common.uti.ConcurrentHashSet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class DefaultSharePublisher extends DefaultPublisher implements ShardedEventPublisher {

    private final Map<Class<? extends SlowEvent>, Set<Subscriber>> subMappings = new ConcurrentHashMap<>();

    private final Lock lock = new ReentrantLock();

    @Override
    public void addSubscriber(Subscriber subscriber, Class<? extends Event> subscribeType) {
        // Actually, do a classification based on the slowEvent type.
        Class<? extends SlowEvent> subSlowEventType = (Class<? extends SlowEvent>) subscribeType;
        // For stop waiting subscriber, see {@link DefaultPublisher#openEventHandler}.
        subscribers.add(subscriber);

        lock.lock();
        try {
            Set<Subscriber> sets = subMappings.get(subSlowEventType);
            if (sets == null) {
                Set<Subscriber> newSet = new ConcurrentHashSet<Subscriber>();
                newSet.add(subscriber);
                subMappings.put(subSlowEventType, newSet);
                return;
            }
            sets.add(subscriber);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeSubscriber(Subscriber subscriber, Class<? extends Event> subscribeType) {
        // Actually, do a classification based on the slowEvent type.
        Class<? extends SlowEvent> subSlowEventType = (Class<? extends SlowEvent>) subscribeType;
        // For removing to parent class attributes synchronization.
        subscribers.remove(subscriber);

        lock.lock();
        try {
            Set<Subscriber> sets = subMappings.get(subSlowEventType);

            if (sets != null) {
                sets.remove(subscriber);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void receiveEvent(Event event) {

        final long currentEventSequence = event.sequence();
        // get subscriber set based on the slow EventType.
        final Class<? extends SlowEvent> slowEventType = (Class<? extends SlowEvent>) event.getClass();

        // Get for Map, the algorithm is O(1).
        Set<Subscriber> subscribers = subMappings.get(slowEventType);
        if (null == subscribers) {
            LOGGER.debug("[NotifyCenter] No subscribers for slow event {}", slowEventType.getName());
            return;
        }

        // Notification single event subscriber
        for (Subscriber subscriber : subscribers) {
            // Whether to ignore expiration events
            if (subscriber.ignoreExpireEvent() && lastEventSequence > currentEventSequence) {
                LOGGER.debug("[NotifyCenter] the {} is unacceptable to this subscriber, because had expire",
                        event.getClass());
                continue;
            }

            // Notify single subscriber for slow event.
            notifySubscriber(subscriber, event);
        }
    }
}