package com.jkoi.notice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "wecom")
public class WeComProperties {

    private String webhookUrl;
    private int timeoutMs;
}
