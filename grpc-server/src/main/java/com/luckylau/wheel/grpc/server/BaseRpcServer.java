package com.luckylau.wheel.grpc.server;

import com.luckylau.wheel.common.PayloadRegistry;
import com.luckylau.wheel.common.uti.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public abstract class BaseRpcServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.luckylau.wheel.grpc.server");

    static {
        PayloadRegistry.init();
    }

    /**
     * Start sever.
     */
    @PostConstruct
    public void start() throws Exception {
        String serverName = getClass().getSimpleName();
        LOGGER.info("{} Rpc server starting at port {}", serverName, getServicePort());

        startServer();

        LOGGER.info("{} Rpc server started at port {}", serverName, getServicePort());
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                LOGGER.info("Nacos {} Rpc server stopping", serverName);
                try {
                    BaseRpcServer.this.stopServer();
                    LOGGER.info("Nacos {} Rpc server stopped successfully...", serverName);
                } catch (Exception e) {
                    LOGGER.error("Nacos {} Rpc server stopped fail...", serverName, e);
                }
            }
        });

    }

    /**
     * Start sever.
     *
     * @throws Exception exception throw if start server fail.
     */
    public abstract void startServer() throws Exception;

    /**
     * get service port.
     *
     * @return service port.
     */
    public int getServicePort() {
        return EnvUtil.generateRandomPort();
    }

    /**
     * Stop Server.
     *
     * @throws Exception throw if stop server fail.
     */
    public final void stopServer() throws Exception {
        shutdownServer();
    }

    /**
     * the increase offset of nacos server port for rpc server port.
     */
    @PreDestroy
    public abstract void shutdownServer();

}
