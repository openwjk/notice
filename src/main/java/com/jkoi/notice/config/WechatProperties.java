package com.jkoi.notice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    private String appId;
    private String secret;
    private String sessionUrl;
    private int timeoutMs;
    private boolean devFallbackEnabled;
}
