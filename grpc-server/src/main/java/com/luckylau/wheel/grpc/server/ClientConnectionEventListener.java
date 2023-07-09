package com.luckylau.wheel.grpc.server;

import javax.annotation.PostConstruct;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public abstract class ClientConnectionEventListener {

    protected ClientConnectionEventListenerRegistry clientConnectionEventListenerRegistry;
    /**
     * listener name.
     */
    private String name;

    @PostConstruct
    public void init() {
        clientConnectionEventListenerRegistry.registerClientConnectionEventListener(this);
    }

    /**
     * Getter method for property <tt>name</tt>.
     *
     * @return property value of name
     */
    public String getName() {
        return name;
    }

    /**
     * Setter method for property <tt>name</tt>.
     *
     * @param name value to be assigned to property name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * notified when a client connected.
     *
     * @param connect connect.
     */
    public abstract void clientConnected(Connection connect);

    /**
     * notified when a client disconnected.
     *
     * @param connect connect.
     */
    public abstract void clientDisConnected(Connection connect);

}

