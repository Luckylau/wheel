package com.luckylau.wheel.common.event;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ConnectionLimitRuleChangeEvent extends Event {
    String limitRule;

    public ConnectionLimitRuleChangeEvent(String limitRule) {
        this.limitRule = limitRule;
    }

    public String getLimitRule() {
        return limitRule;
    }

    public void setLimitRule(String limitRule) {
        this.limitRule = limitRule;
    }
}
