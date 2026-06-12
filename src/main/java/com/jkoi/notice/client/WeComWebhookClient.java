package com.jkoi.notice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jkoi.notice.config.WeComProperties;
import com.jkoi.notice.util.RestTemplateFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class WeComWebhookClient {

    private final WeComProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public WeComWebhookClient(WeComProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = RestTemplateFactory.create(properties.getTimeoutMs());
    }

    public void sendText(String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("msgtype", "text");
        ObjectNode text = body.putObject("text");
        text.put("content", content);

        ResponseEntity<String> response = restTemplate.postForEntity(
                properties.getWebhookUrl(),
                new HttpEntity<>(body.toString(), headers),
                String.class
        );
        assertSuccess(response.getBody());
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.getWebhookUrl());
    }

    private void assertSuccess(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            JsonNode errCode = node.get("errcode");
            if (errCode != null && errCode.asInt() != 0) {
                throw new IllegalStateException("WeCom webhook returned error: " + responseBody);
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ignored) {
            // 某些代理可能不返回 JSON，HTTP 成功即足够
        }
    }
}
