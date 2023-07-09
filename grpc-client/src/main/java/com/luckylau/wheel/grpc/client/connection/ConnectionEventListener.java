package com.luckylau.wheel.grpc.client.connection;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public interface ConnectionEventListener {
    /**
     * notify when  connected to server.
     */
    void onConnected();

    /**
     * notify when  disconnected to server.
     */
    void onDisConnect();
}
