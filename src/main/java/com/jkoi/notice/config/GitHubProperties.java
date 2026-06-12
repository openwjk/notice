package com.jkoi.notice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    private String token;
    private String apiUrl;
    private String filePath;
    private String statsFilePath;
    private String ref;
    private String accept;
    private String apiVersion;
    private int timeoutMs;
}
