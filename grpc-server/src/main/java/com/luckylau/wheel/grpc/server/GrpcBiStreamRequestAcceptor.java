package com.luckylau.wheel.grpc.server;

import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.luckylau.wheel.common.Constants;
import com.luckylau.wheel.common.request.ConnectResetRequest;
import com.luckylau.wheel.common.request.ConnectionSetupRequest;
import com.luckylau.wheel.common.response.Response;
import com.luckylau.wheel.common.uti.GrpcUtils;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.luckylau.wheel.grpc.server.GrpcServer.*;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class GrpcBiStreamRequestAcceptor extends BiRequestStreamGrpc.BiRequestStreamImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcBiStreamRequestAcceptor.class);

    ConnectionManager connectionManager;

    public GrpcBiStreamRequestAcceptor(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }


    private void traceDetailIfNecessary(Payload grpcRequest) {
        String clientIp = grpcRequest.getMetadata().getClientIp();
        String connectionId = CONTEXT_KEY_CONN_ID.get();
        try {
            if (connectionManager.traced(clientIp)) {
                LOGGER.info("[{}]Bi stream request receive, meta={},body={}", connectionId,
                        grpcRequest.getMetadata().toByteString().toStringUtf8(),
                        grpcRequest.getBody().toByteString().toStringUtf8());
            }
        } catch (Throwable throwable) {
            LOGGER.error("[{}]Bi stream request error,payload={},error={}", connectionId,
                    grpcRequest.toByteString().toStringUtf8(), throwable);
        }

    }

    @Override
    public StreamObserver<Payload> requestBiStream(StreamObserver<Payload> responseObserver) {

        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {

            final String connectionId = CONTEXT_KEY_CONN_ID.get();

            final Integer localPort = CONTEXT_KEY_CONN_LOCAL_PORT.get();

            final int remotePort = CONTEXT_KEY_CONN_REMOTE_PORT.get();

            String remoteIp = CONTEXT_KEY_CONN_REMOTE_IP.get();

            String clientIp = "";

            @Override
            public void onNext(Payload payload) {

                clientIp = payload.getMetadata().getClientIp();
                traceDetailIfNecessary(payload);

                Object parseObj;
                try {
                    parseObj = GrpcUtils.parse(payload);
                } catch (Throwable throwable) {
                    LOGGER
                            .warn("[{}]Grpc request bi stream,payload parse error={}", connectionId, throwable);
                    return;
                }

                if (parseObj == null) {
                    LOGGER
                            .warn("[{}]Grpc request bi stream,payload parse null ,body={},meta={}", connectionId,
                                    payload.getBody().getValue().toStringUtf8(), payload.getMetadata());
                    return;
                }
                if (parseObj instanceof ConnectionSetupRequest) {
                    ConnectionSetupRequest setUpRequest = (ConnectionSetupRequest) parseObj;
                    Map<String, String> labels = setUpRequest.getLabels();
                    String appName = "-";
                    if (labels != null && labels.containsKey(Constants.APP_NAME)) {
                        appName = labels.get(Constants.APP_NAME);
                    }

                    ConnectionMeta metaInfo = new ConnectionMeta(connectionId, payload.getMetadata().getClientIp(),
                            remoteIp, remotePort, localPort,
                            setUpRequest.getClientVersion(), appName, setUpRequest.getLabels());
                    metaInfo.setTenant(setUpRequest.getTenant());
                    Connection connection = new Connection(metaInfo, responseObserver, CONTEXT_KEY_CHANNEL.get());

                    if (!connectionManager.register(connectionId, connection)) {
                        //Not register to the connection manager if current server is over limit or server is starting.
                        try {
                            LOGGER.warn("[{}]Connection register fail,reason:{}", connectionId, " server is not started server is over limited.");
                            connection.request(new ConnectResetRequest(), 3000L);
                            connection.close();
                        } catch (Exception e) {
                            //Do nothing.
                            if (connectionManager.traced(clientIp)) {
                                LOGGER
                                        .warn("[{}]Send connect reset request error,error={}", connectionId, e);
                            }
                        }
                    }

                } else if (parseObj instanceof Response) {
                    Response response = (Response) parseObj;
                    if (connectionManager.traced(clientIp)) {
                        LOGGER
                                .warn("[{}]Receive response of server request  ,response={}", connectionId, response);
                    }
                    RpcAckCallbackSynchronizer.ackNotify(connectionId, response);
                    connectionManager.refreshActiveTime(connectionId);
                } else {
                    LOGGER
                            .warn("[{}]Grpc request bi stream,unknown payload receive ,parseObj={}", connectionId,
                                    parseObj);
                }

            }

            @Override
            public void onError(Throwable t) {
                if (connectionManager.traced(clientIp)) {
                    LOGGER.warn("[{}]Bi stream on error,error={}", connectionId, t);
                }

                if (responseObserver instanceof ServerCallStreamObserver) {
                    ServerCallStreamObserver serverCallStreamObserver = ((ServerCallStreamObserver) responseObserver);
                    if (serverCallStreamObserver.isCancelled()) {
                        //client close the stream.
                    } else {
                        try {
                            serverCallStreamObserver.onCompleted();
                        } catch (Throwable throwable) {
                            //ignore
                        }
                    }
                }

            }

            @Override
            public void onCompleted() {
                if (connectionManager.traced(clientIp)) {
                    LOGGER.warn("[{}]Bi stream on completed", connectionId);
                }
                if (responseObserver instanceof ServerCallStreamObserver) {
                    ServerCallStreamObserver serverCallStreamObserver = ((ServerCallStreamObserver) responseObserver);
                    if (serverCallStreamObserver.isCancelled()) {
                        //client close the stream.
                    } else {
                        try {
                            serverCallStreamObserver.onCompleted();
                        } catch (Throwable throwable) {
                            //ignore
                        }

                    }
                }
            }
        };

        return streamObserver;
    }
}