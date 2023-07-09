package com.luckylau.wheel.grpc.client.connection;

import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.luckylau.wheel.common.RpcScheduledExecutor;
import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.request.Request;
import com.luckylau.wheel.common.request.RequestCallBack;
import com.luckylau.wheel.common.request.RequestFuture;
import com.luckylau.wheel.common.response.ErrorResponse;
import com.luckylau.wheel.common.response.Response;
import com.luckylau.wheel.common.response.ResponseCode;
import com.luckylau.wheel.common.uti.GrpcUtils;
import com.luckylau.wheel.grpc.client.RpcClient;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

import javax.annotation.Nullable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class GrpcConnection extends Connection {
    /**
     * grpc channel.
     */
    protected ManagedChannel channel;
    /**
     * stub to send request.
     */
    protected RequestGrpc.RequestFutureStub grpcFutureServiceStub;
    protected StreamObserver<Payload> payloadStreamObserver;
    Executor executor;

    public GrpcConnection(RpcClient.ServerInfo serverInfo, Executor executor) {
        super(serverInfo);
        this.executor = executor;
    }

    @Override
    public Response request(Request request, long timeouts) throws GrpcException {
        //type很重要
        Payload grpcRequest = GrpcUtils.convert(request);
        ListenableFuture<Payload> requestFuture = grpcFutureServiceStub.request(grpcRequest);
        Payload grpcResponse;
        try {
            grpcResponse = requestFuture.get(timeouts, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new GrpcException(GrpcException.SERVER_ERROR, e);
        }

        return (Response) GrpcUtils.parse(grpcResponse);
    }

    @Override
    public RequestFuture requestFuture(Request request) throws GrpcException {
        Payload grpcRequest = GrpcUtils.convert(request);

        final ListenableFuture<Payload> requestFuture = grpcFutureServiceStub.request(grpcRequest);
        return new RequestFuture() {

            @Override
            public boolean isDone() {
                return requestFuture.isDone();
            }

            @Override
            public Response get() throws Exception {
                Payload grpcResponse = requestFuture.get();
                Response response = (Response) GrpcUtils.parse(grpcResponse);
                if (response instanceof ErrorResponse) {
                    throw new GrpcException(response.getErrorCode(), response.getMessage());
                }
                return response;
            }

            @Override
            public Response get(long timeout) throws Exception {
                Payload grpcResponse = requestFuture.get(timeout, TimeUnit.MILLISECONDS);
                Response response = (Response) GrpcUtils.parse(grpcResponse);
                if (response instanceof ErrorResponse) {
                    throw new GrpcException(response.getErrorCode(), response.getMessage());
                }
                return response;
            }
        };
    }

    public void sendResponse(Response response) {
        Payload convert = GrpcUtils.convert(response);
        payloadStreamObserver.onNext(convert);
    }

    public void sendRequest(Request request) {
        Payload convert = GrpcUtils.convert(request);
        payloadStreamObserver.onNext(convert);
    }

    @Override
    public void asyncRequest(Request request, final RequestCallBack requestCallBack) throws GrpcException {
        Payload grpcRequest = GrpcUtils.convert(request);
        ListenableFuture<Payload> requestFuture = grpcFutureServiceStub.request(grpcRequest);

        //set callback .
        Futures.addCallback(requestFuture, new FutureCallback<Payload>() {
            @Override
            public void onSuccess(@Nullable Payload grpcResponse) {
                Response response = (Response) GrpcUtils.parse(grpcResponse);

                if (response != null) {
                    if (response instanceof ErrorResponse) {
                        requestCallBack.onException(new GrpcException(response.getErrorCode(), response.getMessage()));
                    } else {
                        requestCallBack.onResponse(response);
                    }
                } else {
                    requestCallBack.onException(new GrpcException(ResponseCode.FAIL.getCode(), "response is null"));
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                if (throwable instanceof CancellationException) {
                    requestCallBack.onException(
                            new TimeoutException("Timeout after " + requestCallBack.getTimeout() + " milliseconds."));
                } else {
                    requestCallBack.onException(throwable);
                }
            }
        }, requestCallBack.getExecutor() != null ? requestCallBack.getExecutor() : this.executor);
        // set timeout future.
        ListenableFuture<Payload> payloadListenableFuture = Futures
                .withTimeout(requestFuture, requestCallBack.getTimeout(), TimeUnit.MILLISECONDS,
                        RpcScheduledExecutor.TIMEOUT_SCHEDULER);

    }

    @Override
    public void close() {
        if (this.payloadStreamObserver != null) {
            try {
                payloadStreamObserver.onCompleted();
            } catch (Throwable throwable) {
                //ignore.
            }
        }

        if (this.channel != null && !channel.isShutdown()) {
            try {
                this.channel.shutdownNow();
            } catch (Throwable throwable) {
                //ignore.
            }
        }
    }

    /**
     * Getter method for property <tt>channel</tt>.
     *
     * @return property value of channel
     */
    public ManagedChannel getChannel() {
        return channel;
    }

    /**
     * Setter method for property <tt>channel</tt>.
     *
     * @param channel value to be assigned to property channel
     */
    public void setChannel(ManagedChannel channel) {
        this.channel = channel;
    }

    /**
     * Getter method for property <tt>grpcFutureServiceStub</tt>.
     *
     * @return property value of grpcFutureServiceStub
     */
    public RequestGrpc.RequestFutureStub getGrpcFutureServiceStub() {
        return grpcFutureServiceStub;
    }

    /**
     * Setter method for property <tt>grpcFutureServiceStub</tt>.
     *
     * @param grpcFutureServiceStub value to be assigned to property grpcFutureServiceStub
     */
    public void setGrpcFutureServiceStub(RequestGrpc.RequestFutureStub grpcFutureServiceStub) {
        this.grpcFutureServiceStub = grpcFutureServiceStub;
    }

    /**
     * Getter method for property <tt>payloadStreamObserver</tt>.
     *
     * @return property value of payloadStreamObserver
     */
    public StreamObserver<Payload> getPayloadStreamObserver() {
        return payloadStreamObserver;
    }

    /**
     * Setter method for property <tt>payloadStreamObserver</tt>.
     *
     * @param payloadStreamObserver value to be assigned to property payloadStreamObserver
     */
    public void setPayloadStreamObserver(StreamObserver<Payload> payloadStreamObserver) {
        this.payloadStreamObserver = payloadStreamObserver;
    }
}
