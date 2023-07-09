package com.luckylau.wheel.grpc.client.connection;

import com.luckylau.wheel.common.Requester;
import com.luckylau.wheel.common.exception.GrpcException;
import com.luckylau.wheel.common.request.Request;
import com.luckylau.wheel.common.request.RequestCallBack;
import com.luckylau.wheel.common.request.RequestFuture;
import com.luckylau.wheel.common.response.Response;
import com.luckylau.wheel.grpc.client.GrpcClient;


/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class Connection implements Requester {
    private String connectionId;

    private boolean abandon = false;

    private GrpcClient.ServerInfo serverInfo;

    public Connection(GrpcClient.ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }


    @Override
    public Response request(Request request, long timeoutMills) throws GrpcException {
        return null;
    }

    @Override
    public RequestFuture requestFuture(Request request) throws GrpcException {
        return null;
    }

    @Override
    public void asyncRequest(Request request, RequestCallBack requestCallBack) throws GrpcException {

    }

    @Override
    public void close() {

    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    /**
     * Getter method for property <tt>abandon</tt>.
     *
     * @return property value of abandon
     */
    public boolean isAbandon() {
        return abandon;
    }

    /**
     * Setter method for property <tt>abandon</tt>. connection event will be ignored if connection is abandoned.
     *
     * @param abandon value to be assigned to property abandon
     */
    public void setAbandon(boolean abandon) {
        this.abandon = abandon;
    }

    public GrpcClient.ServerInfo getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(GrpcClient.ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }
}
