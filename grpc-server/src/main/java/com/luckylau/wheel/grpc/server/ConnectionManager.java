package com.luckylau.wheel.grpc.server;

import com.luckylau.wheel.common.Constants;
import com.luckylau.wheel.common.NotifyCenter;
import com.luckylau.wheel.common.RpcScheduledExecutor;
import com.luckylau.wheel.common.Subscriber;
import com.luckylau.wheel.common.event.ConnectionLimitRuleChangeEvent;
import com.luckylau.wheel.common.event.Event;
import com.luckylau.wheel.common.exception.ConnectionAlreadyClosedException;
import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.request.ClientDetectionRequest;
import com.luckylau.wheel.common.request.ConnectResetRequest;
import com.luckylau.wheel.common.request.RequestCallBack;
import com.luckylau.wheel.common.request.RequestMeta;
import com.luckylau.wheel.common.response.Response;
import com.luckylau.wheel.common.uti.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ConnectionManager extends Subscriber<ConnectionLimitRuleChangeEvent> {

    public static final String RULE_FILE_NAME = "limitRule";
    private static final Logger LOGGER = LoggerFactory.getLogger("com.luckylau.wheel.grpc.server.connectionManager");
    /**
     * 4 times of client keep alive.
     */
    private static final long KEEP_ALIVE_TIME = 20000L;
    String redirectAddress = null;
    Map<String, Connection> connections = new ConcurrentHashMap<String, Connection>();
    /**
     * connection limit rule.
     */
    private ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
    /**
     * current loader adjust count,only effective once,use to re balance.
     */
    private int loadClient = -1;
    private Map<String, AtomicInteger> connectionForClientIp = new ConcurrentHashMap<String, AtomicInteger>(16);
    private ClientConnectionEventListenerRegistry clientConnectionEventListenerRegistry;

    public ConnectionManager() {
        clientConnectionEventListenerRegistry = new ClientConnectionEventListenerRegistry();
        NotifyCenter.registerToPublisher(ConnectionLimitRuleChangeEvent.class, NotifyCenter.ringBufferSize);
        NotifyCenter.registerSubscriber(this);
    }


    /**
     * if monitor detail.
     *
     * @param clientIp clientIp.
     * @return
     */
    public boolean traced(String clientIp) {
        return connectionLimitRule != null && connectionLimitRule.getMonitorIpList() != null && connectionLimitRule
                .getMonitorIpList().contains(clientIp);
    }

    @PostConstruct
    protected void initLimitRue() {
        try {
            loadRuleFromLocal();
            registerFileWatch();
        } catch (Exception e) {
            LOGGER.warn("Fail to init limit rue from local ,error= ", e);
        }
    }

    /**
     * check connection id is valid.
     *
     * @param connectionId connectionId to be check.
     * @return is valid or not.
     */
    public boolean checkValid(String connectionId) {
        return connections.containsKey(connectionId);
    }

    /**
     * register a new connect.
     *
     * @param connectionId connectionId
     * @param connection   connection
     */
    public synchronized boolean register(String connectionId, Connection connection) {

        if (connection.isConnected()) {
            if (connections.containsKey(connectionId)) {
                return true;
            }
            if (!checkLimit(connection)) {
                return false;
            }
            if (traced(connection.getMetaInfo().clientIp)) {
                connection.setTraced(true);
            }
            connections.put(connectionId, connection);
            connectionForClientIp.get(connection.getMetaInfo().clientIp).getAndIncrement();

            clientConnectionEventListenerRegistry.notifyClientConnected(connection);
            LOGGER
                    .info("new connection registered successfully, connectionId = {},connection={} ", connectionId,
                            connection);
            return true;

        }
        return false;

    }

    private boolean checkLimit(Connection connection) {
        String clientIp = connection.getMetaInfo().clientIp;

        if (isOverLimit()) {
            return false;
        }

        if (!connectionForClientIp.containsKey(clientIp)) {
            connectionForClientIp.putIfAbsent(clientIp, new AtomicInteger(0));
        }

        AtomicInteger currentCount = connectionForClientIp.get(clientIp);

        if (connectionLimitRule != null) {
            // 1.check rule of specific client ip limit.
            if (connectionLimitRule.getCountLimitPerClientIp().containsKey(clientIp)) {
                Integer integer = connectionLimitRule.getCountLimitPerClientIp().get(clientIp);
                if (integer != null && integer >= 0) {
                    return currentCount.get() < integer;
                }
            }
            // 2.check rule of specific client app limit.
            String appName = connection.getMetaInfo().getAppName();
            if (!StringUtils.isBlank(appName) && connectionLimitRule.getCountLimitPerClientApp()
                    .containsKey(appName)) {
                Integer integerApp = connectionLimitRule.getCountLimitPerClientApp().get(appName);
                if (integerApp != null && integerApp >= 0) {
                    return currentCount.get() < integerApp;
                }
            }

            // 3.check rule of default client ip.
            int countLimitPerClientIpDefault = connectionLimitRule.getCountLimitPerClientIpDefault();
            return countLimitPerClientIpDefault <= 0 || currentCount.get() < countLimitPerClientIpDefault;
        }

        return true;

    }

    /**
     * unregister a connection .
     *
     * @param connectionId connectionId.
     */
    public synchronized void unregister(String connectionId) {
        Connection remove = this.connections.remove(connectionId);
        if (remove != null) {
            String clientIp = remove.getMetaInfo().clientIp;
            AtomicInteger atomicInteger = connectionForClientIp.get(clientIp);
            if (atomicInteger != null) {
                int count = atomicInteger.decrementAndGet();
                if (count <= 0) {
                    connectionForClientIp.remove(clientIp);
                }
            }
            remove.close();
            LOGGER.info("[{}]Connection unregistered successfully. ", connectionId);
            clientConnectionEventListenerRegistry.notifyClientDisConnected(remove);
        }
    }

    /**
     * get by connection id.
     *
     * @param connectionId connection id.
     * @return connection of the id.
     */
    public Connection getConnection(String connectionId) {
        return connections.get(connectionId);
    }

    /**
     * get by client ip.
     *
     * @param clientIp client ip.
     * @return connections of the client ip.
     */
    public List<Connection> getConnectionByIp(String clientIp) {
        Set<Map.Entry<String, Connection>> entries = connections.entrySet();
        List<Connection> connections = new ArrayList<>();
        for (Map.Entry<String, Connection> entry : entries) {
            Connection value = entry.getValue();
            if (clientIp.equals(value.getMetaInfo().clientIp)) {
                connections.add(value);
            }
        }
        return connections;
    }

    /**
     * get current connections count.
     *
     * @return get all connection count
     */
    public int getCurrentConnectionCount() {
        return this.connections.size();
    }

    /**
     * regresh connection active time.
     *
     * @param connectionId connectionId.
     */
    public void refreshActiveTime(String connectionId) {
        Connection connection = connections.get(connectionId);
        if (connection != null) {
            connection.freshActiveTime();
        }
    }

    /**
     * Start Task：Expel the connection which active Time expire.
     */
    @PostConstruct
    public void start() {

        // Start UnHealthy Connection Expel Task.
        RpcScheduledExecutor.COMMON_SERVER_EXECUTOR.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {

                    int totalCount = connections.size();
                    LOGGER.info("Connection check task start");
                    Set<Map.Entry<String, Connection>> entries = connections.entrySet();
                    int currentClientsCount = currentClientsCount();
                    boolean isLoaderClient = loadClient >= 0;
                    int currentMaxClient = isLoaderClient ? loadClient : connectionLimitRule.countLimit;
                    int expelCount = currentMaxClient < 0 ? 0 : Math.max(currentClientsCount - currentMaxClient, 0);

                    LOGGER
                            .info("Total count ={}, sdkCount={},clusterCount={}, currentLimit={}, toExpelCount={}",
                                    totalCount, currentClientsCount, (totalCount - currentClientsCount),
                                    currentMaxClient + (isLoaderClient ? "(loaderCount)" : ""), expelCount);

                    List<String> expelClient = new LinkedList<>();

                    Map<String, AtomicInteger> expelForIp = new HashMap<>(16);

                    //1. calculate expel count  of ip.
                    for (Map.Entry<String, Connection> entry : entries) {

                        Connection client = entry.getValue();
                        String appName = client.getMetaInfo().getAppName();
                        String clientIp = client.getMetaInfo().getClientIp();
                        if (!expelForIp.containsKey(clientIp)) {
                            //get limit for current ip.
                            int countLimitOfIp = connectionLimitRule.getCountLimitOfIp(clientIp);
                            if (countLimitOfIp < 0) {
                                int countLimitOfApp = connectionLimitRule.getCountLimitOfApp(appName);
                                countLimitOfIp = countLimitOfApp < 0 ? countLimitOfIp : countLimitOfApp;
                            }
                            if (countLimitOfIp < 0) {
                                countLimitOfIp = connectionLimitRule.getCountLimitPerClientIpDefault();
                            }

                            if (countLimitOfIp >= 0 && connectionForClientIp.containsKey(clientIp)) {
                                AtomicInteger currentCountIp = connectionForClientIp.get(clientIp);
                                if (currentCountIp != null && currentCountIp.get() > countLimitOfIp) {
                                    expelForIp.put(clientIp, new AtomicInteger(currentCountIp.get() - countLimitOfIp));
                                }
                            }
                        }
                    }

                    LOGGER
                            .info("Check over limit for ip limit rule, over limit ip count={}", expelForIp.size());

                    if (expelForIp.size() > 0) {
                        LOGGER.info("Over limit ip expel info, {}", expelForIp);
                    }

                    Set<String> outDatedConnections = new HashSet<>();
                    long now = System.currentTimeMillis();
                    //2.get expel connection for ip limit.
                    for (Map.Entry<String, Connection> entry : entries) {
                        Connection client = entry.getValue();
                        String clientIp = client.getMetaInfo().getClientIp();
                        AtomicInteger integer = expelForIp.get(clientIp);
                        if (integer != null && integer.intValue() > 0) {
                            integer.decrementAndGet();
                            expelClient.add(client.getMetaInfo().getConnectionId());
                            expelCount--;
                        } else if (now - client.getMetaInfo().getLastActiveTime() >= KEEP_ALIVE_TIME) {
                            outDatedConnections.add(client.getMetaInfo().getConnectionId());
                        }

                    }

                    //3. if total count is still over limit.
                    if (expelCount > 0) {
                        for (Map.Entry<String, Connection> entry : entries) {
                            Connection client = entry.getValue();
                            if (!expelForIp.containsKey(client.getMetaInfo().clientIp) && expelCount > 0) {
                                expelClient.add(client.getMetaInfo().getConnectionId());
                                expelCount--;
                                outDatedConnections.remove(client.getMetaInfo().getConnectionId());
                            }
                        }
                    }

                    String serverIp = null;
                    String serverPort = null;
                    if (!StringUtils.isBlank(redirectAddress) && redirectAddress.contains(Constants.COLON)) {
                        String[] split = redirectAddress.split(Constants.COLON);
                        serverIp = split[0];
                        serverPort = split[1];
                    }

                    for (String expelledClientId : expelClient) {
                        try {
                            Connection connection = getConnection(expelledClientId);
                            if (connection != null) {
                                ConnectResetRequest connectResetRequest = new ConnectResetRequest();
                                connectResetRequest.setServerIp(serverIp);
                                connectResetRequest.setServerPort(serverPort);
                                connection.asyncRequest(connectResetRequest, null);
                                LOGGER
                                        .info("Send connection reset request , connection id = {},recommendServerIp={}, recommendServerPort={}",
                                                expelledClientId, connectResetRequest.getServerIp(),
                                                connectResetRequest.getServerPort());
                            }

                        } catch (ConnectionAlreadyClosedException e) {
                            unregister(expelledClientId);
                        } catch (Exception e) {
                            LOGGER.error("Error occurs when expel connection, expelledClientId:{}", expelledClientId, e);
                        }
                    }

                    //4.client active detection.
                    LOGGER.info("Out dated connection ,size={}", outDatedConnections.size());
                    if (CollectionUtils.isNotEmpty(outDatedConnections)) {
                        Set<String> successConnections = new HashSet<>();
                        final CountDownLatch latch = new CountDownLatch(outDatedConnections.size());
                        for (String outDateConnectionId : outDatedConnections) {
                            try {
                                Connection connection = getConnection(outDateConnectionId);
                                if (connection != null) {
                                    ClientDetectionRequest clientDetectionRequest = new ClientDetectionRequest();
                                    connection.asyncRequest(clientDetectionRequest, new RequestCallBack() {
                                        @Override
                                        public Executor getExecutor() {
                                            return null;
                                        }

                                        @Override
                                        public long getTimeout() {
                                            return 1000L;
                                        }

                                        @Override
                                        public void onResponse(Response response) {
                                            latch.countDown();
                                            if (response != null && response.isSuccess()) {
                                                connection.freshActiveTime();
                                                successConnections.add(outDateConnectionId);
                                            }
                                        }

                                        @Override
                                        public void onException(Throwable e) {
                                            latch.countDown();
                                        }
                                    });

                                    LOGGER
                                            .info("[{}]send connection active request ", outDateConnectionId);
                                } else {
                                    latch.countDown();
                                }

                            } catch (ConnectionAlreadyClosedException e) {
                                latch.countDown();
                            } catch (Exception e) {
                                LOGGER
                                        .error("[{}]Error occurs when check client active detection ,error={}",
                                                outDateConnectionId, e);
                                latch.countDown();
                            }
                        }

                        latch.await(3000L, TimeUnit.MILLISECONDS);
                        LOGGER
                                .info("Out dated connection check successCount={}", successConnections.size());

                        for (String outDateConnectionId : outDatedConnections) {
                            if (!successConnections.contains(outDateConnectionId)) {
                                LOGGER
                                        .info("[{}]Unregister Out dated connection....", outDateConnectionId);
                                unregister(outDateConnectionId);
                            }
                        }
                    }

                    //reset loader client

                    if (isLoaderClient) {
                        loadClient = -1;
                        redirectAddress = null;
                    }

                    LOGGER.info("Connection check task end");

                } catch (Throwable e) {
                    LOGGER.error("Error occurs during connection check... ", e);
                }
            }
        }, 1000L, 3000L, TimeUnit.MILLISECONDS);

    }

    private RequestMeta buildMeta() {
        RequestMeta meta = new RequestMeta();
        meta.setClientVersion(VersionUtils.getFullClientVersion());
        meta.setClientIp(NetUtils.localIP());
        return meta;
    }

    public void loadCount(int loadClient, String redirectAddress) {
        this.loadClient = loadClient;
        this.redirectAddress = redirectAddress;
    }

    /**
     * send load request to spefic connetionId.
     *
     * @param connectionId    connection id of client.
     * @param redirectAddress server address to redirect.
     */
    public void loadSingle(String connectionId, String redirectAddress) {
        Connection connection = getConnection(connectionId);

        if (connection != null) {
            ConnectResetRequest connectResetRequest = new ConnectResetRequest();
            if (!StringUtils.isBlank(redirectAddress) && redirectAddress.contains(Constants.COLON)) {
                String[] split = redirectAddress.split(Constants.COLON);
                connectResetRequest.setServerIp(split[0]);
                connectResetRequest.setServerPort(split[1]);
            }
            try {
                connection.request(connectResetRequest, 3000L);
            } catch (ConnectionAlreadyClosedException e) {
                unregister(connectionId);
            } catch (Exception e) {
                LOGGER.error("error occurs when expel connection, connectionId: {} ", connectionId, e);
            }
        }

    }

    /**
     * get all client count.
     *
     * @return client count.
     */
    public int currentClientsCount() {
        return connections.size();
    }

    public Map<String, Connection> currentClients() {
        return connections;
    }

    /**
     * check if over limit.
     *
     * @return over limit or not.
     */
    private boolean isOverLimit() {
        return connectionLimitRule.countLimit > 0 && currentClientsCount() >= connectionLimitRule.getCountLimit();
    }

    @Override
    public void onEvent(ConnectionLimitRuleChangeEvent event) {
        String limitRule = event.getLimitRule();
        LOGGER.info("connection limit rule change event receive :{}", limitRule);

        try {
            ConnectionLimitRule connectionLimitRule = GrpcUtils.toObj(limitRule, ConnectionLimitRule.class);
            if (connectionLimitRule != null) {
                this.connectionLimitRule = connectionLimitRule;

                try {
                    saveRuleToLocal(this.connectionLimitRule);
                } catch (Exception e) {
                    LOGGER.warn("Fail to save rule to local error is ", e);
                }
            } else {
                LOGGER.info("Parse rule is null,Ignore illegal rule  :{}", limitRule);
            }

        } catch (Exception e) {
            LOGGER.error("Fail to parse connection limit rule :{}", limitRule, e);
        }
    }

    @Override
    public Class<? extends Event> subscribeType() {
        return ConnectionLimitRuleChangeEvent.class;
    }

    public ConnectionLimitRule getConnectionLimitRule() {
        return connectionLimitRule;
    }

    private synchronized void loadRuleFromLocal() throws Exception {
        File limitFile = getRuleFile();
        if (!limitFile.exists()) {
            limitFile.createNewFile();
        }

        String ruleContent = DiskUtils.readFile(limitFile);
        ConnectionLimitRule connectionLimitRule = StringUtils.isBlank(ruleContent) ? new ConnectionLimitRule()
                : GrpcUtils.toObj(ruleContent, ConnectionLimitRule.class);
        // apply rule.
        if (connectionLimitRule != null) {
            this.connectionLimitRule = connectionLimitRule;
            Set<String> monitorIpList = connectionLimitRule.monitorIpList;
            for (Connection connection : this.connections.values()) {
                String clientIp = connection.getMetaInfo().getClientIp();
                if (!CollectionUtils.isEmpty(monitorIpList) && monitorIpList.contains(clientIp)) {
                    connection.setTraced(true);
                } else {
                    connection.setTraced(false);
                }
            }

        }
        LOGGER.info("Init loader limit rule from local,rule={}", ruleContent);

    }

    private synchronized void saveRuleToLocal(ConnectionLimitRule limitRule) throws IOException {

        File limitFile = getRuleFile();
        if (!limitFile.exists()) {
            limitFile.createNewFile();
        }
        DiskUtils.writeFile(limitFile, GrpcUtils.toJson(limitRule).getBytes(Constants.ENCODE), false);
    }

    private File getRuleFile() {
        File baseDir = new File("", "data" + File.separator + "loader" + File.separator);
        if (!baseDir.exists()) {
            baseDir.mkdir();
        }
        return new File(baseDir, RULE_FILE_NAME);
    }

    private void registerFileWatch() {
        try {
            String tpsPath = Paths.get(System.getProperty("user.home"), "data", "loader").toString();
            WatchFileCenter.registerWatcher(tpsPath, new FileWatcher() {
                @Override
                public void onChange(FileChangeEvent event) {
                    try {
                        String fileName = event.getContext().toString();
                        if (RULE_FILE_NAME.equals(fileName)) {
                            loadRuleFromLocal();
                        }
                    } catch (Throwable throwable) {
                        LOGGER.warn("Fail to load rule from local", throwable);
                    }
                }

                @Override
                public boolean interest(String context) {
                    return RULE_FILE_NAME.equals(context);
                }
            });
        } catch (GrpcException e) {
            LOGGER.warn("Register  connection rule fail ", e);
        }
    }

    static class ConnectionLimitRule {

        private Set<String> monitorIpList = new HashSet<String>();

        private int countLimit = -1;

        private int countLimitPerClientIpDefault = -1;

        private Map<String, Integer> countLimitPerClientIp = new HashMap<String, Integer>();

        private Map<String, Integer> countLimitPerClientApp = new HashMap<String, Integer>();

        public int getCountLimit() {
            return countLimit;
        }

        public void setCountLimit(int countLimit) {
            this.countLimit = countLimit;
        }

        public int getCountLimitPerClientIpDefault() {
            return countLimitPerClientIpDefault;
        }

        public void setCountLimitPerClientIpDefault(int countLimitPerClientIpDefault) {
            this.countLimitPerClientIpDefault = countLimitPerClientIpDefault;
        }

        public int getCountLimitOfIp(String clientIp) {
            if (countLimitPerClientIp.containsKey(clientIp)) {
                Integer integer = countLimitPerClientIp.get(clientIp);
                if (integer != null && integer >= 0) {
                    return integer;
                }
            }
            return -1;
        }

        public int getCountLimitOfApp(String appName) {
            if (countLimitPerClientApp.containsKey(appName)) {
                Integer integer = countLimitPerClientApp.get(appName);
                if (integer != null && integer >= 0) {
                    return integer;
                }
            }
            return -1;
        }

        public Map<String, Integer> getCountLimitPerClientIp() {
            return countLimitPerClientIp;
        }

        public void setCountLimitPerClientIp(Map<String, Integer> countLimitPerClientIp) {
            this.countLimitPerClientIp = countLimitPerClientIp;
        }

        public Map<String, Integer> getCountLimitPerClientApp() {
            return countLimitPerClientApp;
        }

        public void setCountLimitPerClientApp(Map<String, Integer> countLimitPerClientApp) {
            this.countLimitPerClientApp = countLimitPerClientApp;
        }

        public Set<String> getMonitorIpList() {
            return monitorIpList;
        }

        public void setMonitorIpList(Set<String> monitorIpList) {
            this.monitorIpList = monitorIpList;
        }
    }
}
