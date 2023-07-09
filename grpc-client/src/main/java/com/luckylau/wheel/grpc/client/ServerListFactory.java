package com.luckylau.wheel.grpc.client;

import java.util.List;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public interface ServerListFactory {
    /**
     * switch to a new server and get it.
     *
     * @return server " ip:port".
     */
    String genNextServer();

    /**
     * get current server.
     *
     * @return server " ip:port".
     */
    String getCurrentServer();

    /**
     * get current server.
     *
     * @return servers.
     */
    List<String> getServerList();
}
