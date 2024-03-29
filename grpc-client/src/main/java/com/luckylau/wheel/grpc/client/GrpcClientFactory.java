package com.luckylau.wheel.grpc.client;

import com.luckylau.wheel.common.exception.GrpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class GrpcClientFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.luckylau.wheel.grpc.client");

    private static final Map<String, GrpcClient> CLIENT_MAP = new ConcurrentHashMap<>();

    /**
     * get all client.
     *
     * @return client collection.
     */
    public static Set<Map.Entry<String, GrpcClient>> getAllClientEntries() {
        return CLIENT_MAP.entrySet();
    }

    /**
     * shut down client.
     *
     * @param clientName client name.
     */
    public static void destroyClient(String clientName) throws GrpcException {
        GrpcClient grpcClient = CLIENT_MAP.remove(clientName);
        if (grpcClient != null) {
            grpcClient.shutdown();
        }
    }

    public static GrpcClient getClient(String clientName) {
        return CLIENT_MAP.get(clientName);
    }

    /**
     * create a rpc client.
     *
     * @param clientName client name.
     * @return rpc client.
     */
    public static GrpcClient createClient(String clientName, Map<String, String> labels, ServerListFactory serverListFactory) {
        return CLIENT_MAP.compute(clientName, (clientNameInner, client) -> {
            if (client == null) {
                LOGGER.info("[RpcClientFactory] create a new rpc client of " + clientName);
                client = new GrpcClient(clientNameInner, serverListFactory);
                client.labels(labels);
            }
            return client;
        });
    }
}
