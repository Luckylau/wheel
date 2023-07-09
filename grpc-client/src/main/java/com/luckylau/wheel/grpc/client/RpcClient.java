package com.luckylau.wheel.grpc.client;

import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.luckylau.wheel.common.Constants;
import com.luckylau.wheel.common.PayloadRegistry;
import com.luckylau.wheel.common.ServerRequestHandler;
import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.request.*;
import com.luckylau.wheel.common.response.*;
import com.luckylau.wheel.common.uti.*;
import com.luckylau.wheel.grpc.client.connection.Closeable;
import com.luckylau.wheel.grpc.client.connection.Connection;
import com.luckylau.wheel.grpc.client.connection.ConnectionEventListener;
import com.luckylau.wheel.grpc.client.connection.GrpcConnection;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.luckylau.wheel.common.exception.GrpcException.SERVER_ERROR;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */

public class RpcClient implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.luckylau.wheel.grpc.client");
    private static final int RETRY_TIMES = 3;
    private static final long DEFAULT_TIMEOUT_MILLS = 3000L;
    private static final int DEFAULT_MAX_INBOUND_MESSAGE_SIZE = 10 * 1024 * 1024;
    private static final long DEFAULT_KEEP_ALIVE_TIME = 6 * 60 * 1000;

    static {
        PayloadRegistry.init();
    }

    private final BlockingQueue<ReconnectContext> reconnectionSignal = new ArrayBlockingQueue<ReconnectContext>(1);
    protected LinkedBlockingQueue<ConnectionEvent> eventLinkedBlockingQueue = new LinkedBlockingQueue<ConnectionEvent>();
    protected volatile AtomicReference<RpcClientStatus> rpcClientStatus = new AtomicReference<RpcClientStatus>(
            RpcClientStatus.WAIT_INIT);
    protected ScheduledExecutorService clientEventExecutor;
    protected volatile Connection currentConnection;
    protected Map<String, String> labels = new HashMap<>();
    /**
     * listener called where connection's status changed.
     */
    protected List<ConnectionEventListener> connectionEventListeners = new ArrayList<ConnectionEventListener>();
    /**
     * handlers to process server push request.
     */
    protected List<ServerRequestHandler> serverRequestHandlers = new ArrayList<ServerRequestHandler>();
    private String name;
    private String tenant;
    /**
     * default keep alive time 10s.
     */
    private long keepAliveTime = 5000L;
    private long lastActiveTimeStamp = System.currentTimeMillis();
    private ServerListFactory serverListFactory;
    private ThreadPoolExecutor grpcExecutor = null;

    public RpcClient(ServerListFactory serverListFactory) {
        this.serverListFactory = serverListFactory;
        rpcClientStatus.compareAndSet(RpcClientStatus.WAIT_INIT, RpcClientStatus.INITIALIZED);

    }

    public RpcClient(String name, ServerListFactory serverListFactory) {
        this.serverListFactory = serverListFactory;
        this.name = name;
        rpcClientStatus.compareAndSet(RpcClientStatus.WAIT_INIT, RpcClientStatus.INITIALIZED);
    }

    /**
     * init labels.
     *
     * @param labels labels
     */
    public RpcClient labels(Map<String, String> labels) {
        if (labels != null && !labels.isEmpty()) {
            this.labels.putAll(labels);
        }
        return this;
    }

    /**
     * init keepalive time.
     *
     * @param keepAliveTime keepAliveTime
     * @param timeUnit      timeUnit
     */
    public RpcClient keepAlive(long keepAliveTime, TimeUnit timeUnit) {
        this.keepAliveTime = keepAliveTime * timeUnit.toMillis(keepAliveTime);
        return this;
    }

    /**
     * Notify when client disconnected.
     */
    protected void notifyDisConnected() {
        if (connectionEventListeners.isEmpty()) {
            return;
        }
        for (ConnectionEventListener connectionEventListener : connectionEventListeners) {
            try {
                connectionEventListener.onDisConnect();
            } catch (Throwable throwable) {
                LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Notify disconnect listener error,listener ={}", name,
                        connectionEventListener.getClass().getName());
            }
        }
    }

    /**
     * Notify when client new connected.
     */
    protected void notifyConnected() {
        if (connectionEventListeners.isEmpty()) {
            return;
        }
        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}]Notify connected event to listeners.", name);
        for (ConnectionEventListener connectionEventListener : connectionEventListeners) {
            try {
                connectionEventListener.onConnected();
            } catch (Throwable throwable) {
                LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Notify connect listener error,listener ={}", name,
                        connectionEventListener.getClass().getName());
            }
        }
    }

    /**
     * check is this client is initiated.
     *
     * @return is wait initiated or not.
     */
    public boolean isWaitInitiated() {
        return this.rpcClientStatus.get() == RpcClientStatus.WAIT_INIT;
    }

    /**
     * check is this client is running.
     *
     * @return is running or not.
     */
    public boolean isRunning() {
        return this.rpcClientStatus.get() == RpcClientStatus.RUNNING;
    }

    /**
     * check is this client is shutdown.
     *
     * @return is shutdown or not.
     */
    public boolean isShutdown() {
        return this.rpcClientStatus.get() == RpcClientStatus.SHUTDOWN;
    }

    /**
     * check if current connected server is in serverlist ,if not switch server.
     */
    public void onServerListChange() {
        if (currentConnection != null && currentConnection.getServerInfo() != null) {
            ServerInfo currentServerInfo = currentConnection.getServerInfo();
            boolean found = false;
            for (String serverAddress : serverListFactory.getServerList()) {
                if (serverAddress.equalsIgnoreCase(currentServerInfo.getAddress())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                LoggerUtils.printIfInfoEnabled(LOGGER,
                        "Current connected server {}  is not in latest server list,switch switchServerAsync",
                        currentServerInfo.getAddress());
                switchServerAsync();
            }

        }
    }

    /**
     * Start this client.
     */
    public final void start() throws GrpcException {

        boolean success = rpcClientStatus.compareAndSet(RpcClientStatus.INITIALIZED, RpcClientStatus.STARTING);
        if (!success) {
            return;
        }

        clientEventExecutor = new ScheduledThreadPoolExecutor(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.remote.worker");
                t.setDaemon(true);
                return t;
            }
        });

        // connection event consumer.
        clientEventExecutor.submit(new Runnable() {
            @Override
            public void run() {
                while (!clientEventExecutor.isTerminated() && !clientEventExecutor.isShutdown()) {
                    ConnectionEvent take = null;
                    try {
                        take = eventLinkedBlockingQueue.take();
                        if (take.isConnected()) {
                            notifyConnected();
                        } else if (take.isDisConnected()) {
                            notifyDisConnected();
                        }
                    } catch (Throwable e) {
                        //Do nothing
                    }
                }
            }
        });

        clientEventExecutor.submit(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        if (isShutdown()) {
                            break;
                        }
                        ReconnectContext reconnectContext = reconnectionSignal
                                .poll(keepAliveTime, TimeUnit.MILLISECONDS);
                        if (reconnectContext == null) {
                            //check alive time.
                            if (System.currentTimeMillis() - lastActiveTimeStamp >= keepAliveTime) {
                                boolean isHealthy = healthCheck();
                                if (!isHealthy) {
                                    if (currentConnection == null) {
                                        continue;
                                    }
                                    LoggerUtils.printIfInfoEnabled(LOGGER,
                                            "[{}]Server healthy check fail,currentConnection={}", name,
                                            currentConnection.getConnectionId());

                                    RpcClientStatus rpcClientStatus = RpcClient.this.rpcClientStatus.get();
                                    if (RpcClientStatus.SHUTDOWN.equals(rpcClientStatus)) {
                                        break;
                                    }

                                    boolean success = RpcClient.this.rpcClientStatus
                                            .compareAndSet(rpcClientStatus, RpcClientStatus.UNHEALTHY);
                                    if (success) {
                                        reconnectContext = new ReconnectContext(null, false);
                                    } else {
                                        continue;
                                    }

                                } else {
                                    lastActiveTimeStamp = System.currentTimeMillis();
                                    continue;
                                }
                            } else {
                                continue;
                            }

                        }

                        if (reconnectContext.serverInfo != null) {
                            //clear recommend server if server is not in server list.
                            boolean serverExist = false;
                            for (String server : getServerListFactory().getServerList()) {
                                ServerInfo serverInfo = resolveServerInfo(server);
                                if (serverInfo.getServerIp().equals(reconnectContext.serverInfo.getServerIp())) {
                                    serverExist = true;
                                    reconnectContext.serverInfo.serverPort = serverInfo.serverPort;
                                    break;
                                }
                            }
                            if (!serverExist) {
                                LoggerUtils.printIfInfoEnabled(LOGGER,
                                        "[{}] Recommend server is not in server list ,ignore recommend server {}", name,
                                        reconnectContext.serverInfo.getAddress());

                                reconnectContext.serverInfo = null;

                            }
                        }
                        reconnect(reconnectContext.serverInfo, reconnectContext.onRequestFail);
                    } catch (Throwable throwable) {
                        //Do nothing
                    }
                }
            }
        });

        //connect to server ,try to connect to server sync once, async starting if fail.
        Connection connectToServer = null;
        rpcClientStatus.set(RpcClientStatus.STARTING);

        int startUpRetryTimes = RETRY_TIMES;
        while (startUpRetryTimes > 0 && connectToServer == null) {
            try {
                startUpRetryTimes--;
                ServerInfo serverInfo = nextRpcServer();

                LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Try to connect to server on start up, server: {}", name,
                        serverInfo);

                connectToServer = connectToServer(serverInfo);
            } catch (Throwable e) {
                LoggerUtils.printIfWarnEnabled(LOGGER,
                        "[{}]Fail to connect to server on start up, error message={}, start up retry times left: {}",
                        name, e.getMessage(), startUpRetryTimes);
            }

        }

        if (connectToServer != null) {
            LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Success to connect to server [{}] on start up,connectionId={}",
                    name, connectToServer.getServerInfo().getAddress(), connectToServer.getConnectionId());
            this.currentConnection = connectToServer;
            rpcClientStatus.set(RpcClientStatus.RUNNING);
            eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.CONNECTED));
        } else {
            switchServerAsync();
        }

        registerServerRequestHandler(new ConnectResetRequestHandler());

        //register client detection request.
        registerServerRequestHandler(new ServerRequestHandler() {
            @Override
            public Response requestReply(Request request) {
                if (request instanceof ClientDetectionRequest) {
                    return new ClientDetectionResponse();
                }

                return null;
            }
        });

    }

    public void shutdown() throws GrpcException {
        LOGGER.info("Shutdown rpc client ,set status to shutdown");
        rpcClientStatus.set(RpcClientStatus.SHUTDOWN);
        LOGGER.info("Shutdown  client event executor " + clientEventExecutor);
        clientEventExecutor.shutdownNow();
        LOGGER.info("Close current connection " + currentConnection.getConnectionId());
        closeConnection(currentConnection);
    }

    private boolean healthCheck() {
        HealthCheckRequest healthCheckRequest = new HealthCheckRequest();
        if (this.currentConnection == null) {
            return false;
        }
        try {
            Response response = this.currentConnection.request(healthCheckRequest, 3000L);
            //not only check server is ok ,also check connection is register.
            return response != null && response.
                    isSuccess();
        } catch (GrpcException e) {
            //ignore
        }
        return false;
    }

    public void switchServerAsyncOnRequestFail() {
        switchServerAsync(null, true);
    }

    public void switchServerAsync() {
        switchServerAsync(null, false);
    }

    protected void switchServerAsync(final ServerInfo recommendServerInfo, boolean onRequestFail) {
        reconnectionSignal.offer(new ReconnectContext(recommendServerInfo, onRequestFail));
    }

    /**
     * switch server .
     */
    protected void reconnect(final ServerInfo recommendServerInfo, boolean onRequestFail) {

        try {

            AtomicReference<ServerInfo> recommendServer = new AtomicReference<ServerInfo>(recommendServerInfo);
            if (onRequestFail && healthCheck()) {
                LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Server check success,currentServer is{} ", name,
                        currentConnection.getServerInfo().getAddress());
                rpcClientStatus.set(RpcClientStatus.RUNNING);
                return;
            }

            LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] try to re connect to a new server ,server is {}", name,
                    recommendServerInfo == null ? " not appointed,will choose a random server."
                            : (recommendServerInfo.getAddress() + ", will try it once."));

            // loop until start client success.
            boolean switchSuccess = false;

            int reConnectTimes = 0;
            int retryTurns = 0;
            Exception lastException = null;
            while (!switchSuccess && !isShutdown()) {

                //1.get a new server
                ServerInfo serverInfo = null;
                try {
                    serverInfo = recommendServer.get() == null ? nextRpcServer() : recommendServer.get();
                    //2.create a new channel to new server
                    Connection connectionNew = connectToServer(serverInfo);
                    if (connectionNew != null) {
                        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] success to connect a server  [{}],connectionId={}",
                                name, serverInfo.getAddress(), connectionNew.getConnectionId());
                        //successfully create a new connect.
                        if (currentConnection != null) {
                            LoggerUtils.printIfInfoEnabled(LOGGER,
                                    "[{}] Abandon prev connection ,server is  {}, connectionId is {}", name,
                                    currentConnection.getServerInfo().getAddress(), currentConnection.getConnectionId());
                            //set current connection to enable connection event.
                            currentConnection.setAbandon(true);
                            closeConnection(currentConnection);
                        }
                        currentConnection = connectionNew;
                        rpcClientStatus.set(RpcClientStatus.RUNNING);
                        switchSuccess = true;
                        boolean s = eventLinkedBlockingQueue.add(new ConnectionEvent(ConnectionEvent.CONNECTED));
                        return;
                    }

                    //close connection if client is already shutdown.
                    if (isShutdown()) {
                        closeConnection(currentConnection);
                    }

                    lastException = null;

                } catch (Exception e) {
                    lastException = e;
                } finally {
                    recommendServer.set(null);
                }

                if (reConnectTimes > 0
                        && reConnectTimes % RpcClient.this.serverListFactory.getServerList().size() == 0) {
                    LoggerUtils.printIfInfoEnabled(LOGGER,
                            "[{}] fail to connect server,after trying {} times, last try server is {},error={}", name,
                            reConnectTimes, serverInfo, lastException == null ? "unknown" : lastException);
                    if (Integer.MAX_VALUE == retryTurns) {
                        retryTurns = 50;
                    } else {
                        retryTurns++;
                    }
                }

                reConnectTimes++;

                try {
                    //sleep x milliseconds to switch next server.
                    if (!isRunning()) {
                        // first round ,try servers at a delay 100ms;second round ,200ms; max delays 5s. to be reconsidered.
                        Thread.sleep(Math.min(retryTurns + 1, 50) * 100L);
                    }
                } catch (InterruptedException e) {
                    // Do  nothing.
                }
            }

            if (isShutdown()) {
                LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Client is shutdown ,stop reconnect to server", name);
            }

        } catch (Exception e) {
            LoggerUtils.printIfWarnEnabled(LOGGER, "[{}] Fail to  re connect to server ,error is {}", name, e);
        }
    }

    private void closeConnection(Connection connection) {
        if (connection != null) {
            connection.close();
            eventLinkedBlockingQueue.add(new ConnectionEvent(ConnectionEvent.DISCONNECTED));
        }
    }

    /**
     * increase offset of the nacos server port for the rpc server port.
     *
     * @return rpc port offset
     */
    public int rpcPortOffset() {
        return 1000;
    }

    /**
     * get current server.
     *
     * @return server info.
     */
    public ServerInfo getCurrentServer() {
        if (this.currentConnection != null) {
            return currentConnection.getServerInfo();
        }
        return null;
    }

    ;

    /**
     * send request.
     *
     * @param request request.
     * @return response from server.
     */
    public Response request(Request request) throws GrpcException {
        return request(request, DEFAULT_TIMEOUT_MILLS);
    }

    /**
     * send request.
     *
     * @param request request.
     * @return response from server.
     */
    public Response request(Request request, long timeoutMills) throws GrpcException {
        int retryTimes = 0;
        Response response = null;
        Exception exceptionThrow = null;
        long start = System.currentTimeMillis();
        while (retryTimes < RETRY_TIMES && System.currentTimeMillis() < timeoutMills + start) {
            boolean waitReconnect = false;
            try {
                if (this.currentConnection == null || !isRunning()) {
                    waitReconnect = true;
                    throw new GrpcException(GrpcException.CLIENT_DISCONNECT,
                            "Client not connected,current status:" + rpcClientStatus.get());
                }
                //currentConnection = com.alibaba.nacos.common.remote.client.grpc.GrpcConnection
                response = this.currentConnection.request(request, timeoutMills);
                if (response == null) {
                    throw new GrpcException(SERVER_ERROR, "Unknown Exception.");
                }
                if (response instanceof ErrorResponse) {
                    if (response.getErrorCode() == GrpcException.UN_REGISTER) {
                        synchronized (this) {
                            waitReconnect = true;
                            if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                                LoggerUtils.printIfErrorEnabled(LOGGER,
                                        "Connection is unregistered, switch server,connectionId={},request={}",
                                        currentConnection.getConnectionId(), request.getClass().getSimpleName());
                                switchServerAsync();
                            }
                        }

                    }
                    throw new GrpcException(response.getErrorCode(), response.getMessage());
                }
                // return response.
                lastActiveTimeStamp = System.currentTimeMillis();
                return response;

            } catch (Exception e) {
                if (waitReconnect) {
                    try {
                        //wait client to re connect.
                        Thread.sleep(Math.min(100, timeoutMills / 3));
                    } catch (Exception exception) {
                        //Do nothing.
                    }
                }

                LoggerUtils.printIfErrorEnabled(LOGGER, "Send request fail, request={}, retryTimes={},errorMessage={}",
                        request, retryTimes, e.getMessage());

                exceptionThrow = e;

            }
            retryTimes++;

        }

        if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
            switchServerAsyncOnRequestFail();
        }

        if (exceptionThrow != null) {
            throw (exceptionThrow instanceof GrpcException) ? (GrpcException) exceptionThrow
                    : new GrpcException(SERVER_ERROR, exceptionThrow);
        } else {
            throw new GrpcException(SERVER_ERROR, "Request fail,Unknown Error");
        }
    }

    /**
     * send async request.
     *
     * @param request request.
     */
    public void asyncRequest(Request request, RequestCallBack callback) throws GrpcException {
        int retryTimes = 0;

        Exception exceptionToThrow = null;
        long start = System.currentTimeMillis();
        while (retryTimes < RETRY_TIMES && System.currentTimeMillis() < start + callback.getTimeout()) {
            boolean waitReconnect = false;
            try {
                if (this.currentConnection == null || !isRunning()) {
                    waitReconnect = true;
                    throw new GrpcException(GrpcException.CLIENT_INVALID_PARAM, "Client not connected.");
                }
                // 使用GrpcConnection发送异步请求
                this.currentConnection.asyncRequest(request, callback);
                return;
            } catch (Exception e) {
                if (waitReconnect) {
                    try {
                        //wait client to re connect.
                        Thread.sleep(Math.min(100, callback.getTimeout() / 3));
                    } catch (Exception exception) {
                        //Do nothing.
                    }
                }
                LoggerUtils
                        .printIfErrorEnabled(LOGGER, "[{}]Send request fail, request={}, retryTimes={},errorMessage={}",
                                name, request, retryTimes, e.getMessage());
                exceptionToThrow = e;

            }
            retryTimes++;

        }
        // 判断RpcClientStatus 是不是RUNNING状态，若不是则设置其为UNHEALTHY
        if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
            switchServerAsyncOnRequestFail();
        }
        if (exceptionToThrow != null) {
            throw (exceptionToThrow instanceof GrpcException) ? (GrpcException) exceptionToThrow
                    : new GrpcException(SERVER_ERROR, exceptionToThrow);
        } else {
            throw new GrpcException(SERVER_ERROR, "AsyncRequest fail, unknown error");
        }
    }

    /**
     * send async request.
     *
     * @param request request.
     * @return request future.
     */
    public RequestFuture requestFuture(Request request) throws GrpcException {
        int retryTimes = 0;
        long start = System.currentTimeMillis();
        Exception exceptionToThrow = null;
        while (retryTimes < RETRY_TIMES && System.currentTimeMillis() < start + DEFAULT_TIMEOUT_MILLS) {
            boolean waitReconnect = false;
            try {
                if (this.currentConnection == null || !isRunning()) {
                    waitReconnect = true;
                    throw new GrpcException(GrpcException.CLIENT_INVALID_PARAM, "Client not connected.");
                }
                return this.currentConnection.requestFuture(request);
            } catch (Exception e) {
                if (waitReconnect) {
                    try {
                        //wait client to re connect.
                        Thread.sleep(100L);
                    } catch (Exception exception) {
                        //Do nothing.
                    }
                }
                LoggerUtils
                        .printIfErrorEnabled(LOGGER, "[{}]Send request fail, request={}, retryTimes={},errorMessage={}",
                                name, request, retryTimes, e.getMessage());
                exceptionToThrow = e;

            }
        }

        if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
            switchServerAsyncOnRequestFail();
        }

        if (exceptionToThrow != null) {
            throw (exceptionToThrow instanceof GrpcException) ? (GrpcException) exceptionToThrow
                    : new GrpcException(SERVER_ERROR, exceptionToThrow);
        } else {
            throw new GrpcException(SERVER_ERROR, "Request future fail, unknown error");
        }

    }

    /**
     * connect to server.
     *
     * @param serverInfo server address to connect.
     * @return return connection when successfully connect to server, or null if failed.
     * @throws Exception exception when fail to connect to server.
     */
    public Connection connectToServer(ServerInfo serverInfo) throws Exception {
        try {
            if (grpcExecutor == null) {
                int threadNumber = ThreadUtils.getSuitableThreadCount(8);
                grpcExecutor = new ThreadPoolExecutor(threadNumber, threadNumber, 10L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(10000),
                        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("grpc-client-executor-%d")
                                .build());
                grpcExecutor.allowCoreThreadTimeOut(true);

            }
            int port = serverInfo.getServerPort() + rpcPortOffset();
            RequestGrpc.RequestFutureStub newChannelStubTemp = createNewChannelStub(serverInfo.getServerIp(), port);
            if (newChannelStubTemp != null) {

                Response response = serverCheck(serverInfo.getServerIp(), port, newChannelStubTemp);
                if (response == null || !(response instanceof ServerCheckResponse)) {
                    shuntDownChannel((ManagedChannel) newChannelStubTemp.getChannel());
                    return null;
                }

                BiRequestStreamGrpc.BiRequestStreamStub biRequestStreamStub = BiRequestStreamGrpc
                        .newStub(newChannelStubTemp.getChannel());
                GrpcConnection grpcConn = new GrpcConnection(serverInfo, grpcExecutor);
                grpcConn.setConnectionId(((ServerCheckResponse) response).getConnectionId());

                //create stream request and bind connection event to this connection.
                StreamObserver<Payload> payloadStreamObserver = bindRequestStream(biRequestStreamStub, grpcConn);

                // stream observer to send response to server
                grpcConn.setPayloadStreamObserver(payloadStreamObserver);
                grpcConn.setGrpcFutureServiceStub(newChannelStubTemp);
                grpcConn.setChannel((ManagedChannel) newChannelStubTemp.getChannel());
                //send a  setup request.
                ConnectionSetupRequest conSetupRequest = new ConnectionSetupRequest();
                conSetupRequest.setClientVersion(VersionUtils.getFullClientVersion());
                conSetupRequest.setLabels(getLabels());
                conSetupRequest.setTenant(getTenant());
                grpcConn.sendRequest(conSetupRequest);
                //wait to register connection setup
                Thread.sleep(100L);
                return grpcConn;
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("[{}]Fail to connect to server!,error={}", RpcClient.this.getName(), e);
        }
        return null;
    }

    private StreamObserver<Payload> bindRequestStream(final BiRequestStreamGrpc.BiRequestStreamStub streamStub,
                                                      final GrpcConnection grpcConn) {

        return streamStub.requestBiStream(new StreamObserver<Payload>() {

            @Override
            public void onNext(Payload payload) {

                LoggerUtils.printIfDebugEnabled(LOGGER, "[{}]Stream server request receive, original info: {}",
                        grpcConn.getConnectionId(), payload.toString());
                try {
                    Object parseBody = GrpcUtils.parse(payload);
                    final Request request = (Request) parseBody;
                    if (request != null) {

                        try {
                            Response response = handleServerRequest(request);
                            if (response != null) {
                                response.setRequestId(request.getRequestId());
                                sendResponse(response);
                            } else {
                                LOGGER.warn("[{}]Fail to process server request, ackId->{}", grpcConn.getConnectionId(),
                                        request.getRequestId());
                            }

                        } catch (Exception e) {
                            LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Handle server request exception: {}",
                                    grpcConn.getConnectionId(), payload.toString(), e.getMessage());
                            sendResponse(request.getRequestId(), false);
                        }

                    }

                } catch (Exception e) {

                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Error to process server push response: {}",
                            grpcConn.getConnectionId(), payload.getBody().getValue().toStringUtf8());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                boolean isRunning = isRunning();
                boolean isAbandon = grpcConn.isAbandon();
                if (isRunning && !isAbandon) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Request stream error, switch server,error={}",
                            grpcConn.getConnectionId(), throwable);
                    if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                        switchServerAsync();
                    }

                } else {
                    LoggerUtils.printIfWarnEnabled(LOGGER, "[{}]Ignore error event,isRunning:{},isAbandon={}",
                            grpcConn.getConnectionId(), isRunning, isAbandon);
                }

            }

            @Override
            public void onCompleted() {
                boolean isRunning = isRunning();
                boolean isAbandon = grpcConn.isAbandon();
                if (isRunning && !isAbandon) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Request stream onCompleted, switch server",
                            grpcConn.getConnectionId());
                    if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                        switchServerAsync();
                    }

                } else {
                    LoggerUtils.printIfInfoEnabled(LOGGER, "[{}]Ignore complete event,isRunning:{},isAbandon={}",
                            grpcConn.getConnectionId(), isRunning, isAbandon);
                }

            }
        });
    }

    private void sendResponse(Response response) {
        try {
            ((GrpcConnection) this.currentConnection).sendResponse(response);
        } catch (Exception e) {
            LOGGER.error("[{}]Error to send ack response, ackId->{}", this.currentConnection.getConnectionId(),
                    response.getRequestId());
        }
    }

    private void sendResponse(String ackId, boolean success) {
        try {
            PushAckRequest request = PushAckRequest.build(ackId, success);
            this.currentConnection.request(request, 3000L);
        } catch (Exception e) {
            LOGGER.error("[{}]Error to send ack response, ackId->{}", this.currentConnection.getConnectionId(), ackId);
        }
    }

    /**
     * shutdown a  channel.
     *
     * @param managedChannel channel to be shutdown.
     */
    private void shuntDownChannel(ManagedChannel managedChannel) {
        if (managedChannel != null && !managedChannel.isShutdown()) {
            managedChannel.shutdownNow();
        }
    }

    /**
     * create a new channel with specific server address.
     *
     * @param serverIp   serverIp.
     * @param serverPort serverPort.
     * @return if server check success,return a non-null stub.
     */
    private RequestGrpc.RequestFutureStub createNewChannelStub(String serverIp, int serverPort) {

        ManagedChannelBuilder<?> o = ManagedChannelBuilder.forAddress(serverIp, serverPort).executor(grpcExecutor)
                .compressorRegistry(CompressorRegistry.getDefaultInstance())
                .decompressorRegistry(DecompressorRegistry.getDefaultInstance())
                .maxInboundMessageSize(DEFAULT_MAX_INBOUND_MESSAGE_SIZE)
                //配置keepAlive
                .keepAliveTime(DEFAULT_KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS).usePlaintext();

        ManagedChannel managedChannelTemp = o.build();

        return RequestGrpc.newFutureStub(managedChannelTemp);
    }

    /**
     * check server if success.
     *
     * @param requestBlockingStub requestBlockingStub used to check server.
     * @return success or not
     */
    private Response serverCheck(String ip, int port, RequestGrpc.RequestFutureStub requestBlockingStub) {
        try {
            if (requestBlockingStub == null) {
                return null;
            }
            ServerCheckRequest serverCheckRequest = new ServerCheckRequest();
            Payload grpcRequest = GrpcUtils.convert(serverCheckRequest);
            ListenableFuture<Payload> responseFuture = requestBlockingStub.request(grpcRequest);
            Payload response = responseFuture.get(3000L, TimeUnit.MILLISECONDS);
            //receive connection unregister response here,not check response is success.
            return (Response) GrpcUtils.parse(response);
        } catch (Exception e) {
            LoggerUtils.printIfErrorEnabled(LOGGER,
                    "Server check fail, please check server {} ,port {} is available , error ={}", ip, port, e);
            return null;
        }
    }

    /**
     * handle server request.
     *
     * @param request request.
     * @return response.
     */
    protected Response handleServerRequest(final Request request) {

        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}]receive server push request,request={},requestId={}", name,
                request.getClass().getSimpleName(), request.getRequestId());
        lastActiveTimeStamp = System.currentTimeMillis();
        for (ServerRequestHandler serverRequestHandler : serverRequestHandlers) {
            try {
                Response response = serverRequestHandler.requestReply(request);

                if (response != null) {
                    LoggerUtils.printIfInfoEnabled(LOGGER, "[{}]ack server push request,request={},requestId={}", name,
                            request.getClass().getSimpleName(), request.getRequestId());
                    return response;
                }
            } catch (Exception e) {
                LoggerUtils.printIfInfoEnabled(LOGGER, "[{}]handleServerRequest:{}, errorMessage={}", name,
                        serverRequestHandler.getClass().getName(), e.getMessage());
            }

        }
        return null;
    }

    /**
     * Register connection handler. Will be notified when inner connection's state changed.
     *
     * @param connectionEventListener connectionEventListener
     */
    public synchronized void registerConnectionListener(ConnectionEventListener connectionEventListener) {

        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}]Registry connection listener to current client:{}", name,
                connectionEventListener.getClass().getName());
        this.connectionEventListeners.add(connectionEventListener);
    }

    /**
     * Register serverRequestHandler, the handler will handle the request from server side.
     *
     * @param serverRequestHandler serverRequestHandler
     */
    public synchronized void registerServerRequestHandler(ServerRequestHandler serverRequestHandler) {
        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}]Register server push request handler:{}", name,
                serverRequestHandler.getClass().getName());

        this.serverRequestHandlers.add(serverRequestHandler);
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
     * Getter method for property <tt>serverListFactory</tt>.
     *
     * @return property value of serverListFactory
     */
    public ServerListFactory getServerListFactory() {
        return serverListFactory;
    }

    protected ServerInfo nextRpcServer() {
        String serverAddress = getServerListFactory().genNextServer();
        return resolveServerInfo(serverAddress);
    }

    protected ServerInfo currentRpcServer() {
        String serverAddress = getServerListFactory().getCurrentServer();
        return resolveServerInfo(serverAddress);
    }

    /**
     * Getter method for property <tt>labels</tt>.
     *
     * @return property value of labels
     */
    public Map<String, String> getLabels() {
        return labels;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    private ServerInfo resolveServerInfo(String serverAddress) {
        ServerInfo serverInfo;
        String[] split = serverAddress.split(Constants.COLON);
        serverInfo = new ServerInfo(split[0], Integer.parseInt(split[1]));
        return serverInfo;
    }

    public static class ServerInfo {

        protected String serverIp;

        protected int serverPort;

        public ServerInfo() {

        }

        public ServerInfo(String serverIp, int serverPort) {
            this.serverPort = serverPort;
            this.serverIp = serverIp;
        }

        /**
         * get address, ip:port.
         *
         * @return address.
         */
        public String getAddress() {
            return serverIp + Constants.COLON + serverPort;
        }

        /**
         * Getter method for property <tt>serverIp</tt>.
         *
         * @return property value of serverIp
         */
        public String getServerIp() {
            return serverIp;
        }

        /**
         * Setter method for property <tt>serverIp</tt>.
         *
         * @param serverIp value to be assigned to property serverIp
         */
        public void setServerIp(String serverIp) {
            this.serverIp = serverIp;
        }

        /**
         * Getter method for property <tt>serverPort</tt>.
         *
         * @return property value of serverPort
         */
        public int getServerPort() {
            return serverPort;
        }

        /**
         * Setter method for property <tt>serverPort</tt>.
         *
         * @param serverPort value to be assigned to property serverPort
         */
        public void setServerPort(int serverPort) {
            this.serverPort = serverPort;
        }

        @Override
        public String toString() {
            return "{serverIp='" + serverIp + '\'' + ", server main port=" + serverPort + '}';
        }
    }

    class ConnectResetRequestHandler implements ServerRequestHandler {

        @Override
        public Response requestReply(Request request) {

            if (request instanceof ConnectResetRequest) {

                try {
                    synchronized (RpcClient.this) {
                        if (isRunning()) {
                            ConnectResetRequest connectResetRequest = (ConnectResetRequest) request;
                            if (!StringUtils.isBlank(connectResetRequest.getServerIp())) {
                                ServerInfo serverInfo = resolveServerInfo(
                                        connectResetRequest.getServerIp() + Constants.COLON + connectResetRequest
                                                .getServerPort());
                                switchServerAsync(serverInfo, false);
                            } else {
                                switchServerAsync();
                            }
                        }
                    }
                } catch (Exception e) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Switch server error ,{}", name, e);
                }
                return new ConnectResetResponse();
            }
            return null;
        }
    }

    public class ConnectionEvent {

        public static final int CONNECTED = 1;

        public static final int DISCONNECTED = 0;

        int eventType;

        public ConnectionEvent(int eventType) {
            this.eventType = eventType;
        }

        public boolean isConnected() {
            return eventType == CONNECTED;
        }

        public boolean isDisConnected() {
            return eventType == DISCONNECTED;
        }
    }

    class ReconnectContext {

        boolean onRequestFail;
        ServerInfo serverInfo;

        public ReconnectContext(ServerInfo serverInfo, boolean onRequestFail) {
            this.onRequestFail = onRequestFail;
            this.serverInfo = serverInfo;
        }
    }
}
