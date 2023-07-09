package com.luckylau.wheel.grpc.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ClientConnectionEventListenerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnectionEventListenerRegistry.class);

    final List<ClientConnectionEventListener> clientConnectionEventListeners = new ArrayList<ClientConnectionEventListener>();

    /**
     * notify where a new client connected.
     *
     * @param connection connection that new created.
     */
    public void notifyClientConnected(final Connection connection) {

        for (ClientConnectionEventListener clientConnectionEventListener : clientConnectionEventListeners) {
            try {
                clientConnectionEventListener.clientConnected(connection);
            } catch (Throwable throwable) {
                LOGGER.info("[NotifyClientConnected] failed for listener {}", clientConnectionEventListener.getName(),
                        throwable);
            }
        }

    }

    /**
     * notify where a new client disconnected.
     *
     * @param connection connection that disconnected.
     */
    public void notifyClientDisConnected(final Connection connection) {

        for (ClientConnectionEventListener clientConnectionEventListener : clientConnectionEventListeners) {
            try {
                clientConnectionEventListener.clientDisConnected(connection);
            } catch (Throwable throwable) {
                LOGGER.info("[NotifyClientDisConnected] failed for listener {}",
                        clientConnectionEventListener.getName(), throwable);
            }
        }

    }

    /**
     * register ClientConnectionEventListener.
     *
     * @param listener listener.
     */
    public void registerClientConnectionEventListener(ClientConnectionEventListener listener) {
        LOGGER.info("[ClientConnectionEventListenerRegistry] registry listener - " + listener.getClass()
                .getSimpleName());
        this.clientConnectionEventListeners.add(listener);
    }
}
