package com.luckylau.wheel.grpc.server;

import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.luckylau.wheel.common.RequestHandler;
import com.luckylau.wheel.common.RequestHandlerRegistry;
import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.request.Request;
import com.luckylau.wheel.common.request.RequestMeta;
import com.luckylau.wheel.common.request.ServerCheckRequest;
import com.luckylau.wheel.common.response.ErrorResponse;
import com.luckylau.wheel.common.response.Response;
import com.luckylau.wheel.common.response.ResponseCode;
import com.luckylau.wheel.common.response.ServerCheckResponse;
import com.luckylau.wheel.common.uti.GrpcUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.luckylau.wheel.grpc.server.GrpcServer.CONTEXT_KEY_CONN_ID;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class GrpcRequestAcceptor extends RequestGrpc.RequestImplBase {

    protected static final Logger LOGGER = LoggerFactory.getLogger(GrpcRequestAcceptor.class);


    private RequestHandlerRegistry requestHandlerRegistry;

    private ConnectionManager connectionManager;

    public GrpcRequestAcceptor(RequestHandlerRegistry requestHandlerRegistry, ConnectionManager connectionManager) {
        this.requestHandlerRegistry = requestHandlerRegistry;
        this.connectionManager = connectionManager;
    }

    private void traceIfNecessary(Payload grpcRequest, boolean receive) {
        String clientIp = grpcRequest.getMetadata().getClientIp();
        String connectionId = CONTEXT_KEY_CONN_ID.get();
        try {
            if (connectionManager.traced(clientIp)) {
                LOGGER.info("[{}]Payload {},meta={},body={}", connectionId, receive ? "receive" : "send",
                        grpcRequest.getMetadata().toByteString().toStringUtf8(),
                        grpcRequest.getBody().toByteString().toStringUtf8());
            }
        } catch (Throwable throwable) {
            LOGGER.error("[{}]Monitor request error,payload={},error={}", clientIp,
                    grpcRequest.toByteString().toStringUtf8(), throwable);
        }

    }

    /**
     * grpc服务入口
     *
     * @param grpcRequest
     * @param responseObserver
     */
    @Override
    public void request(Payload grpcRequest, StreamObserver<Payload> responseObserver) {
        // 如果有必要跟踪的话
        traceIfNecessary(grpcRequest, true);
        // 获取请求对象中的请求类型
        String type = grpcRequest.getMetadata().getType();
        // 判断请求是否为ServerCheckRequest
        // server check.
        if (ServerCheckRequest.class.getSimpleName().equals(type)) {
            // 构建一个ServerCheckResponse作为返回对象，并包装为Payload返回给调用方
            Payload serverCheckResponseP = GrpcUtils.convert(new ServerCheckResponse(CONTEXT_KEY_CONN_ID.get()));
            traceIfNecessary(serverCheckResponseP, false);
            responseObserver.onNext(serverCheckResponseP);
            responseObserver.onCompleted();
            return;
        }
        //获取RequestHandler
        RequestHandler requestHandler = requestHandlerRegistry.getByRequestType(type);
        //no handler found.
        if (requestHandler == null) {
            LOGGER.warn(String.format("[%s] No handler for request type : %s :", "grpc", type));
            Payload payloadResponse = GrpcUtils
                    .convert(buildErrorResponse(GrpcException.NO_HANDLER, "RequestHandler Not Found"));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
            return;
        }

        //check connection status.
        // 检查连接状态
        String connectionId = CONTEXT_KEY_CONN_ID.get();
        boolean requestValid = connectionManager.checkValid(connectionId);
        if (!requestValid) {
            LOGGER
                    .warn("[{}] Invalid connection Id ,connection [{}] is un registered ,", "grpc", connectionId);
            Payload payloadResponse = GrpcUtils
                    .convert(buildErrorResponse(GrpcException.UN_REGISTER, "Connection is unregistered."));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
            return;
        }
        // 解析请求对象
        Object parseObj;
        try {
            parseObj = GrpcUtils.parse(grpcRequest);
        } catch (Exception e) {
            LOGGER
                    .warn("[{}] Invalid request receive from connection [{}] ,error={}", "grpc", connectionId, e);
            Payload payloadResponse = GrpcUtils.convert(buildErrorResponse(GrpcException.BAD_GATEWAY, e.getMessage()));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
            return;
        }

        // 请求对象为空，返回
        if (parseObj == null) {
            LOGGER.warn("[{}] Invalid request receive  ,parse request is null", connectionId);
            Payload payloadResponse = GrpcUtils
                    .convert(buildErrorResponse(GrpcException.BAD_GATEWAY, "Invalid request"));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
        }
        // 请求对象货不对板，返回
        if (!(parseObj instanceof Request)) {
            LOGGER
                    .warn("[{}] Invalid request receive  ,parsed payload is not a request,parseObj={}", connectionId,
                            parseObj);
            Payload payloadResponse = GrpcUtils
                    .convert(buildErrorResponse(GrpcException.BAD_GATEWAY, "Invalid request"));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
            return;
        }

        // 将接收的对象转换为请求对象
        Request request = (Request) parseObj;
        try {
            Connection connection = connectionManager.getConnection(CONTEXT_KEY_CONN_ID.get());
            RequestMeta requestMeta = new RequestMeta();
            requestMeta.setClientIp(connection.getMetaInfo().getClientIp());
            requestMeta.setConnectionId(CONTEXT_KEY_CONN_ID.get());
            requestMeta.setClientVersion(connection.getMetaInfo().getVersion());
            requestMeta.setLabels(connection.getMetaInfo().getLabels());
            connectionManager.refreshActiveTime(requestMeta.getConnectionId());
            Response response = requestHandler.handleRequest(request, requestMeta);
            Payload payloadResponse = GrpcUtils.convert(response);
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
        } catch (Throwable e) {
            LOGGER
                    .error("[{}] Fail to handle request from connection [{}] ,error message :{}", "grpc", connectionId,
                            e);
            Payload payloadResponse = GrpcUtils.convert(buildErrorResponse(
                    (e instanceof GrpcException) ? ((GrpcException) e).getErrCode() : ResponseCode.FAIL.getCode(),
                    e.getMessage()));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
        }

    }

    private Response buildErrorResponse(int errorCode, String msg) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorInfo(errorCode, msg);
        return response;
    }
}
