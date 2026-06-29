package com.jkoi.notice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /**
     * Token 有效时长（小时），默认 24 小时
     */
    private int tokenExpireHours = 24;

    /**
     * 验证码长度，默认 6 位
     */
    private int codeLength = 6;

    /**
     * 验证码有效时长（分钟），默认 5 分钟
     */
    private int codeExpireMinutes = 5;

    /**
     * 管理员标识，用于验证码发送目标标识
     */
    private String adminKey = "";
}
