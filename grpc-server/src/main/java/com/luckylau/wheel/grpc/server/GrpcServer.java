package com.luckylau.wheel.grpc.server;

import com.alibaba.nacos.api.grpc.auto.Payload;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.luckylau.wheel.common.RequestHandlerRegistry;
import com.luckylau.wheel.common.uti.ReflectUtils;
import com.luckylau.wheel.common.uti.StringUtils;
import io.grpc.*;
import io.grpc.internal.ServerStream;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.util.MutableHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class GrpcServer extends BaseRpcServer {
    static final Attributes.Key<String> TRANS_KEY_CONN_ID = Attributes.Key.create("conn_id");
    static final Attributes.Key<String> TRANS_KEY_REMOTE_IP = Attributes.Key.create("remote_ip");
    static final Attributes.Key<Integer> TRANS_KEY_REMOTE_PORT = Attributes.Key.create("remote_port");
    static final Attributes.Key<Integer> TRANS_KEY_LOCAL_PORT = Attributes.Key.create("local_port");
    static final Context.Key<String> CONTEXT_KEY_CONN_ID = Context.key("conn_id");
    static final Context.Key<String> CONTEXT_KEY_CONN_REMOTE_IP = Context.key("remote_ip");
    static final Context.Key<Integer> CONTEXT_KEY_CONN_REMOTE_PORT = Context.key("remote_port");
    static final Context.Key<Integer> CONTEXT_KEY_CONN_LOCAL_PORT = Context.key("local_port");
    static final Context.Key<Channel> CONTEXT_KEY_CHANNEL = Context.key("ctx_channel");
    private static final Logger LOGGER = LoggerFactory.getLogger("com.luckylau.wheel.grpc.client");
    private static final String REQUEST_BI_STREAM_SERVICE_NAME = "BiRequestStream";
    private static final String REQUEST_BI_STREAM_METHOD_NAME = "requestBiStream";
    private static final String REQUEST_SERVICE_NAME = "Request";
    private static final String REQUEST_METHOD_NAME = "request";
    private static final String GRPC_MAX_INBOUND_MSG_SIZE_PROPERTY = "remote.server.grpc.maxinbound.message.size";
    private static final long DEFAULT_GRPC_MAX_INBOUND_MSG_SIZE = 10 * 1024 * 1024;
    /**
     * Default remote execute queue size: 16384.
     */
    private static final int REMOTE_EXECUTOR_QUEUE_SIZE = 1 << 14;
    private final GrpcRequestAcceptor grpcCommonRequestAcceptor;
    private final GrpcBiStreamRequestAcceptor grpcBiStreamRequestAcceptor;

    ;
    private final ConnectionManager connectionManager;
    private Server server;

    public GrpcServer() {
        connectionManager = new ConnectionManager();
        grpcBiStreamRequestAcceptor = new GrpcBiStreamRequestAcceptor(connectionManager);
        grpcCommonRequestAcceptor = new GrpcRequestAcceptor(new RequestHandlerRegistry(), connectionManager);

    }

    @Override
    public void startServer() throws Exception {
        final MutableHandlerRegistry handlerRegistry = new MutableHandlerRegistry();

        // server interceptor to set connection id.
        ServerInterceptor serverInterceptor = new ServerInterceptor() {
            @Override
            public <T, S> ServerCall.Listener<T> interceptCall(ServerCall<T, S> call, Metadata headers,
                                                               ServerCallHandler<T, S> next) {
                Context ctx = Context.current()
                        .withValue(CONTEXT_KEY_CONN_ID, call.getAttributes().get(TRANS_KEY_CONN_ID))
                        .withValue(CONTEXT_KEY_CONN_REMOTE_IP, call.getAttributes().get(TRANS_KEY_REMOTE_IP))
                        .withValue(CONTEXT_KEY_CONN_REMOTE_PORT, call.getAttributes().get(TRANS_KEY_REMOTE_PORT))
                        .withValue(CONTEXT_KEY_CONN_LOCAL_PORT, call.getAttributes().get(TRANS_KEY_LOCAL_PORT));
                if (REQUEST_BI_STREAM_SERVICE_NAME.equals(call.getMethodDescriptor().getServiceName())) {
                    Channel internalChannel = getInternalChannel(call);
                    ctx = ctx.withValue(CONTEXT_KEY_CHANNEL, internalChannel);
                }
                return Contexts.interceptCall(ctx, call, headers, next);
            }
        };
        //
        addServices(handlerRegistry, serverInterceptor);

        server = ServerBuilder.forPort(getServicePort()).executor(getRpcExecutor())
                .maxInboundMessageSize(getInboundMessageSize()).fallbackHandlerRegistry(handlerRegistry)
                .compressorRegistry(CompressorRegistry.getDefaultInstance())
                .decompressorRegistry(DecompressorRegistry.getDefaultInstance())
                .addTransportFilter(new ServerTransportFilter() {
                    @Override
                    public Attributes transportReady(Attributes transportAttrs) {
                        InetSocketAddress remoteAddress = (InetSocketAddress) transportAttrs
                                .get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
                        InetSocketAddress localAddress = (InetSocketAddress) transportAttrs
                                .get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR);
                        int remotePort = remoteAddress.getPort();
                        int localPort = localAddress.getPort();
                        String remoteIp = remoteAddress.getAddress().getHostAddress();
                        Attributes attrWrapper = transportAttrs.toBuilder()
                                .set(TRANS_KEY_CONN_ID, System.currentTimeMillis() + "_" + remoteIp + "_" + remotePort)
                                .set(TRANS_KEY_REMOTE_IP, remoteIp).set(TRANS_KEY_REMOTE_PORT, remotePort)
                                .set(TRANS_KEY_LOCAL_PORT, localPort).build();
                        String connectionId = attrWrapper.get(TRANS_KEY_CONN_ID);
                        LOGGER.info("Connection transportReady,connectionId = {} ", connectionId);
                        return attrWrapper;

                    }

                    @Override
                    public void transportTerminated(Attributes transportAttrs) {
                        String connectionId = null;
                        try {
                            connectionId = transportAttrs.get(TRANS_KEY_CONN_ID);
                        } catch (Exception e) {
                            // Ignore
                        }
                        if (!StringUtils.isBlank(connectionId)) {
                            LOGGER.info("Connection transportTerminated,connectionId = {} ", connectionId);
                            connectionManager.unregister(connectionId);
                        }
                    }
                }).build();

        server.start();
    }

    private int getInboundMessageSize() {
        String messageSize = System
                .getProperty(GRPC_MAX_INBOUND_MSG_SIZE_PROPERTY, String.valueOf(DEFAULT_GRPC_MAX_INBOUND_MSG_SIZE));
        return Integer.parseInt(messageSize);
    }

    private Channel getInternalChannel(ServerCall serverCall) {
        ServerStream serverStream = (ServerStream) ReflectUtils.getFieldValue(serverCall, "stream");
        return (Channel) ReflectUtils.getFieldValue(serverStream, "channel");
    }

    private void addServices(MutableHandlerRegistry handlerRegistry, ServerInterceptor... serverInterceptor) {

        // unary common call register.
        final MethodDescriptor<Payload, Payload> unaryPayloadMethod = MethodDescriptor.<Payload, Payload>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(REQUEST_SERVICE_NAME, REQUEST_METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();

        final ServerCallHandler<Payload, Payload> payloadHandler = ServerCalls
                .asyncUnaryCall((request, responseObserver) -> {
                    grpcCommonRequestAcceptor.request(request, responseObserver);
                });

        final ServerServiceDefinition serviceDefOfUnaryPayload = ServerServiceDefinition.builder(REQUEST_SERVICE_NAME)
                .addMethod(unaryPayloadMethod, payloadHandler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfUnaryPayload, serverInterceptor));

        // bi stream register.
        final ServerCallHandler<Payload, Payload> biStreamHandler = ServerCalls.asyncBidiStreamingCall(
                (responseObserver) -> grpcBiStreamRequestAcceptor.requestBiStream(responseObserver));

        final MethodDescriptor<Payload, Payload> biStreamMethod = MethodDescriptor.<Payload, Payload>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING).setFullMethodName(MethodDescriptor
                        .generateFullMethodName(REQUEST_BI_STREAM_SERVICE_NAME, REQUEST_BI_STREAM_METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(Payload.newBuilder().build()))
                .setResponseMarshaller(ProtoUtils.marshaller(Payload.getDefaultInstance())).build();

        final ServerServiceDefinition serviceDefOfBiStream = ServerServiceDefinition
                .builder(REQUEST_BI_STREAM_SERVICE_NAME).addMethod(biStreamMethod, biStreamHandler).build();
        handlerRegistry.addService(ServerInterceptors.intercept(serviceDefOfBiStream, serverInterceptor));

    }

    @Override
    public void shutdownServer() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    /**
     * get rpc executor.
     *
     * @return executor.
     */
    public ThreadPoolExecutor getRpcExecutor() {
        return new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().availableProcessors(), 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(REMOTE_EXECUTOR_QUEUE_SIZE),
                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("grpc-executor-%d").build());
    }
}
