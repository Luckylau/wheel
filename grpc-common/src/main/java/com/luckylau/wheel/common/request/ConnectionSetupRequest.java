package com.luckylau.wheel.common.request;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ConnectionSetupRequest extends InternalRequest {

    private String clientVersion;

    private String tenant;

    private Map<String, String> labels = new HashMap<String, String>();

    public ConnectionSetupRequest() {
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

}
