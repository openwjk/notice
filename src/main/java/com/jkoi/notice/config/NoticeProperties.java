package com.jkoi.notice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "notice")
public class NoticeProperties {

    private boolean enabled;
    private long fixedDelayMs;
    private long initialDelayMs;
    private String cronField;
    private String dataField;
    private int maxContentLength;
}
