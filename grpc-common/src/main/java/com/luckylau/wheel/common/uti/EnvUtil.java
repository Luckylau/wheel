package com.luckylau.wheel.common.uti;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class EnvUtil {

    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;

    public static int generateRandomPort() {
        int randomPort = generate();

        while (!isPortAvailable(randomPort)) {
            randomPort = generate();
        }

        return randomPort;
    }

    private static int generate() {
        return (int) (Math.random() * (MAX_PORT - MIN_PORT + 1) + EnvUtil.MIN_PORT);
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
