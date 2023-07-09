package com.luckylau.wheel.grpc.server;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ConnectionMeta {

    protected Map<String, String> labels = new HashMap<String, String>();
    /**
     * Client IP Address.
     */
    String clientIp;
    /**
     * Remote IP Address.
     */
    String remoteIp;
    /**
     * Remote IP Port.
     */
    int remotePort;
    /**
     * Local Ip Port.
     */
    int localPort;
    /**
     * Client version.
     */
    String version;
    /**
     * Identify Unique connectionId.
     */
    String connectionId;
    /**
     * create time.
     */
    Date createTime;
    /**
     * astActiveTime.
     */
    long lastActiveTime;
    /**
     * String appName.
     */
    String appName;
    /**
     * tenant.
     */
    String tenant;

    public ConnectionMeta(String connectionId, String clientIp, String remoteIp, int remotePort, int localPort,
                          String version, String appName, Map<String, String> labels) {
        this.connectionId = connectionId;
        this.clientIp = clientIp;
        this.version = version;
        this.appName = appName;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.localPort = localPort;
        this.createTime = new Date();
        this.lastActiveTime = System.currentTimeMillis();
        this.labels.putAll(labels);
    }

    public String getLabel(String labelKey) {
        return labels.get(labelKey);
    }

    /**
     * Getter method for property <tt>labels</tt>.
     *
     * @return property value of labels
     */
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * Setter method for property <tt>labels</tt>.
     *
     * @param labels value to be assigned to property labels
     */
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    /**
     * Getter method for property <tt>clientIp</tt>.
     *
     * @return property value of clientIp
     */
    public String getClientIp() {
        return clientIp;
    }

    /**
     * Setter method for property <tt>clientIp</tt>.
     *
     * @param clientIp value to be assigned to property clientIp
     */
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    /**
     * Getter method for property <tt>connectionId</tt>.
     *
     * @return property value of connectionId
     */
    public String getConnectionId() {
        return connectionId;
    }

    /**
     * Setter method for property <tt>connectionId</tt>.
     *
     * @param connectionId value to be assigned to property connectionId
     */
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    /**
     * Getter method for property <tt>createTime</tt>.
     *
     * @return property value of createTime
     */
    public Date getCreateTime() {
        return createTime;
    }

    /**
     * Setter method for property <tt>createTime</tt>.
     *
     * @param createTime value to be assigned to property createTime
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    /**
     * Getter method for property <tt>lastActiveTime</tt>.
     *
     * @return property value of lastActiveTime
     */
    public long getLastActiveTime() {
        return lastActiveTime;
    }

    /**
     * Setter method for property <tt>lastActiveTime</tt>.
     *
     * @param lastActiveTime value to be assigned to property lastActiveTime
     */
    public void setLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    /**
     * Getter method for property <tt>version</tt>.
     *
     * @return property value of version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Setter method for property <tt>version</tt>.
     *
     * @param version value to be assigned to property version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Getter method for property <tt>localPort</tt>.
     *
     * @return property value of localPort
     */
    public int getLocalPort() {
        return localPort;
    }

    /**
     * Setter method for property <tt>localPort</tt>.
     *
     * @param localPort value to be assigned to property localPort
     */
    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
