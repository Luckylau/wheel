package com.luckylau.wheel.grpc.server;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class RpcAckCallbackSynchronizer {
    public static final Map<String, Map<String, DefaultRequestFuture>> CALLBACK_CONTEXT = new ConcurrentLinkedHashMap.Builder<String, Map<String, DefaultRequestFuture>>()
            .maximumWeightedCapacity(1000000)
            .listener((s, pushCallBack) -> pushCallBack.entrySet().forEach(new Consumer<Map.Entry<String, DefaultRequestFuture>>() {
                @Override
                public void accept(Map.Entry<String, DefaultRequestFuture> stringDefaultPushFutureEntry) {
                    stringDefaultPushFutureEntry.getValue().setFailResult(new TimeoutException());
                }
            })).build();
    private static final Logger LOGGER = LoggerFactory.getLogger("com.luckylau.wheel.grpc.server.rpcAckCallbackSynchronizer");

    /**
     * notify  ack.
     */
    public static void ackNotify(String connectionId, Response response) {
        Map<String, DefaultRequestFuture> stringDefaultPushFutureMap = CALLBACK_CONTEXT.get(connectionId);
        if (stringDefaultPushFutureMap == null) {
            LOGGER.warn("Ack receive on a outdated connection ,connection id={},requestId={} ", connectionId,
                    response.getRequestId());
            return;
        }

        DefaultRequestFuture currentCallback = stringDefaultPushFutureMap.remove(response.getRequestId());
        if (currentCallback == null) {
            LOGGER.warn("Ack receive on a outdated request ,connection id={},requestId={} ", connectionId,
                    response.getRequestId());
            return;
        }

        if (response.isSuccess()) {
            currentCallback.setResponse(response);
        } else {
            currentCallback.setFailResult(new GrpcException(response.getErrorCode(), response.getMessage()));
        }
    }

    /**
     * notify  ackid.
     */
    public static void syncCallback(String connectionId, String requestId, DefaultRequestFuture defaultPushFuture)
            throws GrpcException {

        Map<String, DefaultRequestFuture> stringDefaultPushFutureMap = initContextIfNecessary(connectionId);
        ;
        if (!stringDefaultPushFutureMap.containsKey(requestId)) {
            DefaultRequestFuture pushCallBackPrev = stringDefaultPushFutureMap
                    .putIfAbsent(requestId, defaultPushFuture);
            if (pushCallBackPrev == null) {
                return;
            }
        }
        throw new GrpcException(GrpcException.INVALID_PARAM, "request id confilict");

    }

    /**
     * clear context of connectionId.
     *
     * @param connectionId connectionId
     */
    public static void clearContext(String connectionId) {
        CALLBACK_CONTEXT.remove(connectionId);
    }

    /**
     * clear context of connectionId.
     *
     * @param connectionId connectionId
     */
    public static Map<String, DefaultRequestFuture> initContextIfNecessary(String connectionId) {
        if (!CALLBACK_CONTEXT.containsKey(connectionId)) {
            Map<String, DefaultRequestFuture> context = new HashMap<String, DefaultRequestFuture>(128);
            Map<String, DefaultRequestFuture> stringDefaultRequestFutureMap = CALLBACK_CONTEXT
                    .putIfAbsent(connectionId, context);
            return stringDefaultRequestFutureMap == null ? context : stringDefaultRequestFutureMap;
        } else {
            return CALLBACK_CONTEXT.get(connectionId);
        }
    }

    /**
     * clear context of connectionId.
     *
     * @param connectionId connectionId
     */
    public static void clearFuture(String connectionId, String requestId) {
        Map<String, DefaultRequestFuture> stringDefaultPushFutureMap = CALLBACK_CONTEXT.get(connectionId);

        if (stringDefaultPushFutureMap == null || !stringDefaultPushFutureMap.containsKey(requestId)) {
            return;
        }
        stringDefaultPushFutureMap.remove(requestId);
    }
}
