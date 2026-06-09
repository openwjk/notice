package com.jkoi.notice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    private String appId;
    private String secret;
    private String sessionUrl;
    private int timeoutMs;
    private boolean devFallbackEnabled;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSessionUrl() {
        return sessionUrl;
    }

    public void setSessionUrl(String sessionUrl) {
        this.sessionUrl = sessionUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isDevFallbackEnabled() {
        return devFallbackEnabled;
    }

    public void setDevFallbackEnabled(boolean devFallbackEnabled) {
        this.devFallbackEnabled = devFallbackEnabled;
    }
}
