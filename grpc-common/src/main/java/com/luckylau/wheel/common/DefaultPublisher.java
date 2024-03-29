package com.luckylau.wheel.common;

import com.luckylau.wheel.common.event.Event;
import com.luckylau.wheel.common.uti.CollectionUtils;
import com.luckylau.wheel.common.uti.ConcurrentHashSet;
import com.luckylau.wheel.common.uti.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static com.luckylau.wheel.common.NotifyCenter.ringBufferSize;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class DefaultPublisher extends Thread implements EventPublisher {

    protected static final Logger LOGGER = LoggerFactory.getLogger(NotifyCenter.class);
    private static final AtomicReferenceFieldUpdater<DefaultPublisher, Long> UPDATER = AtomicReferenceFieldUpdater
            .newUpdater(DefaultPublisher.class, Long.class, "lastEventSequence");
    protected final ConcurrentHashSet<Subscriber> subscribers = new ConcurrentHashSet<>();
    protected volatile Long lastEventSequence = -1L;
    private volatile boolean initialized = false;
    private volatile boolean shutdown = false;
    private Class<? extends Event> eventType;
    private int queueMaxSize = -1;
    private BlockingQueue<Event> queue;

    @Override
    public void init(Class<? extends Event> type, int bufferSize) {
        setDaemon(true);
        setName("nacos.publisher-" + type.getName());
        this.eventType = type;
        this.queueMaxSize = bufferSize;
        this.queue = new ArrayBlockingQueue<>(bufferSize);
        start();
    }

    public ConcurrentHashSet<Subscriber> getSubscribers() {
        return subscribers;
    }

    @Override
    public synchronized void start() {
        if (!initialized) {
            // start just called once
            super.start();
            if (queueMaxSize == -1) {
                queueMaxSize = ringBufferSize;
            }
            initialized = true;
        }
    }

    @Override
    public long currentEventSize() {
        return queue.size();
    }

    @Override
    public void run() {
        openEventHandler();
    }

    void openEventHandler() {
        try {

            // This variable is defined to resolve the problem which message overstock in the queue.
            int waitTimes = 60;
            // To ensure that messages are not lost, enable EventHandler when
            // waiting for the first Subscriber to register
            for (; ; ) {
                if (shutdown || hasSubscriber() || waitTimes <= 0) {
                    break;
                }
                ThreadUtils.sleep(1000L);
                waitTimes--;
            }

            for (; ; ) {
                if (shutdown) {
                    break;
                }
                final Event event = queue.take();
                receiveEvent(event);
                UPDATER.compareAndSet(this, lastEventSequence, Math.max(lastEventSequence, event.sequence()));
            }
        } catch (Throwable ex) {
            LOGGER.error("Event listener exception : ", ex);
        }
    }

    private boolean hasSubscriber() {
        return CollectionUtils.isNotEmpty(subscribers);
    }

    @Override
    public void addSubscriber(Subscriber subscriber) {
        subscribers.add(subscriber);
    }

    @Override
    public void removeSubscriber(Subscriber subscriber) {
        subscribers.remove(subscriber);
    }

    @Override
    public boolean publish(Event event) {
        checkIsStart();
        boolean success = this.queue.offer(event);
        if (!success) {
            LOGGER.warn("Unable to plug in due to interruption, synchronize sending time, event : {}", event);
            receiveEvent(event);
            return true;
        }
        return true;
    }

    void checkIsStart() {
        if (!initialized) {
            throw new IllegalStateException("Publisher does not start");
        }
    }

    @Override
    public void shutdown() {
        this.shutdown = true;
        this.queue.clear();
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Receive and notifySubscriber to process the event.
     *
     * @param event {@link Event}.
     */
    void receiveEvent(Event event) {
        final long currentEventSequence = event.sequence();

        if (!hasSubscriber()) {
            LOGGER.warn("[NotifyCenter] the {} is lost, because there is no subscriber.");
            return;
        }

        // Notification single event listener
        for (Subscriber subscriber : subscribers) {
            // Whether to ignore expiration events
            if (subscriber.ignoreExpireEvent() && lastEventSequence > currentEventSequence) {
                LOGGER.debug("[NotifyCenter] the {} is unacceptable to this subscriber, because had expire",
                        event.getClass());
                continue;
            }

            // Because unifying smartSubscriber and subscriber, so here need to think of compatibility.
            // Remove original judge part of codes.
            notifySubscriber(subscriber, event);
        }
    }

    @Override
    public void notifySubscriber(final Subscriber subscriber, final Event event) {

        LOGGER.debug("[NotifyCenter] the {} will received by {}", event, subscriber);

        final Runnable job = () -> subscriber.onEvent(event);
        final Executor executor = subscriber.executor();

        if (executor != null) {
            executor.execute(job);
        } else {
            try {
                job.run();
            } catch (Throwable e) {
                LOGGER.error("Event callback exception: ", e);
            }
        }
    }
}
