package com.luckylau.wheel.common.request;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ConnectResetRequest extends ServerRequest {
    private static final String MODULE = "internal";

    String serverIp;

    String serverPort;

    @Override
    public String getModule() {
        return MODULE;
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
    public String getServerPort() {
        return serverPort;
    }

    /**
     * Setter method for property <tt>serverPort</tt>.
     *
     * @param serverPort value to be assigned to property serverPort
     */
    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }
}
