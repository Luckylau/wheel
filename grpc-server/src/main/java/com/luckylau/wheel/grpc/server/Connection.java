package com.luckylau.wheel.grpc.server;

import com.alibaba.nacos.api.grpc.auto.Payload;
import com.luckylau.wheel.common.Requester;
import com.luckylau.wheel.common.exception.ConnectionAlreadyClosedException;
import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.request.Request;
import com.luckylau.wheel.common.request.RequestCallBack;
import com.luckylau.wheel.common.request.RequestFuture;
import com.luckylau.wheel.common.response.Response;
import com.luckylau.wheel.common.uti.GrpcUtils;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class Connection implements Requester {

    private static final Logger LOGGER = LoggerFactory.getLogger("com.luckylau.wheel.grpc.server.connection");
    private final ConnectionMeta metaInfo;
    private Channel channel;
    private StreamObserver streamObserver;
    private boolean traced = false;

    public Connection(ConnectionMeta metaInfo, StreamObserver streamObserver, Channel channel) {
        this.metaInfo = metaInfo;
        this.streamObserver = streamObserver;
        this.channel = channel;
    }

    private void sendRequestNoAck(Request request) throws GrpcException {
        try {
            //StreamObserver#onNext() is not thread-safe,synchronized is required to avoid direct memory leak.
            synchronized (streamObserver) {

                Payload payload = GrpcUtils.convert(request);
                traceIfNecessary(payload);
                streamObserver.onNext(payload);
            }
        } catch (Exception e) {
            if (e instanceof StatusRuntimeException) {
                throw new ConnectionAlreadyClosedException(e);
            }
            throw e;
        }
    }

    private void traceIfNecessary(Payload payload) {
        String connectionId = null;
        if (this.isTraced()) {
            try {
                connectionId = getMetaInfo().getConnectionId();
                LOGGER.info("[{}]Send request to client ,payload={}", connectionId,
                        payload.toByteString().toStringUtf8());
            } catch (Throwable throwable) {
                LOGGER
                        .warn("[{}]Send request to client trace error, ,error={}", connectionId, throwable);
            }
        }
    }

    private DefaultRequestFuture sendRequestInner(Request request, RequestCallBack callBack) throws GrpcException {
        final String requestId = String.valueOf(PushAckIdGenerator.getNextId());
        request.setRequestId(requestId);

        DefaultRequestFuture defaultPushFuture = new DefaultRequestFuture(getMetaInfo().getConnectionId(), requestId,
                callBack, () -> RpcAckCallbackSynchronizer.clearFuture(getMetaInfo().getConnectionId(), requestId));

        RpcAckCallbackSynchronizer.syncCallback(getMetaInfo().getConnectionId(), requestId, defaultPushFuture);
        sendRequestNoAck(request);
        return defaultPushFuture;
    }

    @Override
    public Response request(Request request, long timeoutMills) throws GrpcException {
        DefaultRequestFuture pushFuture = sendRequestInner(request, null);
        try {
            return pushFuture.get(timeoutMills);
        } catch (Exception e) {
            throw new GrpcException(GrpcException.SERVER_ERROR, e);
        } finally {
            RpcAckCallbackSynchronizer.clearFuture(getMetaInfo().getConnectionId(), pushFuture.getRequestId());
        }
    }

    @Override
    public RequestFuture requestFuture(Request request) throws GrpcException {
        return sendRequestInner(request, null);
    }

    @Override
    public void asyncRequest(Request request, RequestCallBack requestCallBack) throws GrpcException {
        sendRequestInner(request, requestCallBack);
    }

    @Override
    public void close() {
        String connectionId = null;

        try {
            connectionId = getMetaInfo().getConnectionId();

            if (isTraced()) {
                LOGGER.warn("[{}] try to close connection ", connectionId);
            }

            closeBiStream();
            channel.close();

        } catch (Exception e) {
            LOGGER.warn("[{}] connection  close exception  : {}", connectionId, e);
        }
    }

    private void closeBiStream() {
        if (streamObserver instanceof ServerCallStreamObserver) {
            ServerCallStreamObserver serverCallStreamObserver = ((ServerCallStreamObserver) streamObserver);
            if (!serverCallStreamObserver.isCancelled()) {
                serverCallStreamObserver.onCompleted();
            }
        }
    }


    public Map<String, String> getLabels() {
        return metaInfo.getLabels();
    }

    public boolean isTraced() {
        return traced;
    }

    public void setTraced(boolean traced) {
        this.traced = traced;
    }

    /**
     * check is connected.
     *
     * @return if connection or not,check the inner connection is active.
     */
    public boolean isConnected() {
        return channel != null && channel.isOpen() && channel.isActive();
    }

    ;

    /**
     * Update last Active Time to now.
     */
    public void freshActiveTime() {
        metaInfo.setLastActiveTime(System.currentTimeMillis());
    }

    /**
     * Getter method for property <tt>metaInfo</tt>.
     *
     * @return property value of metaInfo
     */
    public ConnectionMeta getMetaInfo() {
        return metaInfo;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
