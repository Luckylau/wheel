package com.luckylau.wheel.common.response;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ServerCheckResponse extends Response {
    private String connectionId;

    public ServerCheckResponse() {

    }

    public ServerCheckResponse(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
}
