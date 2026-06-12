package com.jkoi.notice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkoi.notice.config.WechatProperties;
import com.jkoi.notice.util.RestTemplateFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class WechatIdentityService {

    private final WechatProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public WechatIdentityService(WechatProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = RestTemplateFactory.create(properties.getTimeoutMs() <= 0 ? 5000 : properties.getTimeoutMs());
    }

    public String resolveWxId(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getSecret())) {
            return resolveDevWxId(code);
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl(properties.getSessionUrl())
                    .queryParam("appid", properties.getAppId())
                    .queryParam("secret", properties.getSecret())
                    .queryParam("js_code", code)
                    .queryParam("grant_type", "authorization_code")
                    .toUriString();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            JsonNode openId = root.get("openid");
            String wxId = openId == null || openId.isNull() ? "" : openId.asText("");
            return StringUtils.hasText(wxId) ? wxId : resolveDevWxId(code);
        } catch (Exception ignored) {
            return resolveDevWxId(code);
        }
    }

    private String resolveDevWxId(String code) {
        if (!properties.isDevFallbackEnabled()) {
            return "";
        }
        return "dev-" + sha256(code).substring(0, 24);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (Exception ex) {
            return String.valueOf(Math.abs(value.hashCode()));
        }
    }
}
