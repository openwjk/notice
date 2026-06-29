package com.jkoi.notice.service;

import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务：管理验证码的生成/发送/验证，以及 Token 的签发/校验/刷新。
 * <p>
 * 验证码流程：
 * 1. 前端请求发送验证码 → 后端生成6位数字验证码 → 通过企微机器人发送
 * 2. 用户在登录页输入验证码 → 后端校验 → 签发 Token
 * <p>
 * Token 流程：
 * - Token 有效期 24 小时，每次有 API 活动自动续期
 * - Token 存储在内存中（单实例部署足够）
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthProperties properties;
    private final WeComWebhookClient weComWebhookClient;
    private final SecureRandom random = new SecureRandom();

    /**
     * 验证码存储: key=验证码, value=过期时间
     */
    private final Map<String, LocalDateTime> codeStore = new ConcurrentHashMap<>();

    /**
     * Token 存储: key=token, value=过期时间
     */
    private final Map<String, LocalDateTime> tokenStore = new ConcurrentHashMap<>();

    public AuthService(AuthProperties properties, WeComWebhookClient weComWebhookClient) {
        this.properties = properties;
        this.weComWebhookClient = weComWebhookClient;
    }

    /**
     * 生成验证码并通过企微机器人发送。
     *
     * @return true=发送成功, false=发送失败
     */
    public boolean sendVerificationCode() {
        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(properties.getCodeExpireMinutes());
        codeStore.put(code, expiresAt);

        // 清理过期验证码
        cleanupExpiredCodes();

        String message = String.format(
                "【给你两拳】验证码：%s\n%d 分钟内有效，请勿泄露。",
                code, properties.getCodeExpireMinutes()
        );

        try {
            if (weComWebhookClient.isConfigured()) {
                weComWebhookClient.sendText(message);
                log.info("Verification code sent via WeCom webhook.");
                return true;
            } else {
                log.warn("WeCom webhook not configured, code is: {}", code);
                // 开发环境：未配置 webhook 时仍允许生成验证码，但打印到日志
                return true;
            }
        } catch (Exception ex) {
            log.error("Failed to send verification code via WeCom webhook.", ex);
            return false;
        }
    }

    /**
     * 验证验证码并签发 Token。
     *
     * @param code 用户输入的验证码
     * @return Token 字符串，验证失败返回 null
     */
    public String verifyAndIssueToken(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }

        LocalDateTime expiresAt = codeStore.remove(code);
        if (expiresAt == null) {
            log.warn("Invalid or used verification code.");
            return null;
        }

        if (LocalDateTime.now().isAfter(expiresAt)) {
            log.warn("Verification code expired.");
            return null;
        }

        // 签发 Token
        String token = generateToken();
        LocalDateTime tokenExpiresAt = LocalDateTime.now().plusHours(properties.getTokenExpireHours());
        tokenStore.put(token, tokenExpiresAt);

        // 清理过期 Token
        cleanupExpiredTokens();

        log.info("Token issued successfully, expires at: {}", tokenExpiresAt);
        return token;
    }

    /**
     * 校验 Token 是否有效。如果有效，自动刷新时效。
     *
     * @param token 请求携带的 Token
     * @return true=有效, false=无效或过期
     */
    public boolean validateAndRefreshToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        LocalDateTime expiresAt = tokenStore.get(token);
        if (expiresAt == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(expiresAt)) {
            tokenStore.remove(token);
            return false;
        }

        // 刷新 Token 时效
        tokenStore.put(token, LocalDateTime.now().plusHours(properties.getTokenExpireHours()));
        return true;
    }

    /**
     * 注销 Token。
     */
    public void revokeToken(String token) {
        if (StringUtils.hasText(token)) {
            tokenStore.remove(token);
        }
    }

    /**
     * 直接签发 Token（仅供测试或管理使用，跳过验证码校验）。
     *
     * @return 签发的 Token 字符串
     */
    public String issueTokenDirectly() {
        String token = generateToken();
        LocalDateTime tokenExpiresAt = LocalDateTime.now().plusHours(properties.getTokenExpireHours());
        tokenStore.put(token, tokenExpiresAt);
        log.info("Token issued directly, expires at: {}", tokenExpiresAt);
        return token;
    }

    // ======================== 内部方法 ========================

    private String generateCode() {
        int length = properties.getCodeLength();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(64);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private void cleanupExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        codeStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }

    private void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        tokenStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }
}
