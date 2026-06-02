package com.jkoi.notice.client;

import com.jkoi.notice.config.WeComProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class WeComWebhookClient {

    private final WeComProperties properties;
    private final ObjectMapper objectMapper;

    public WeComWebhookClient(WeComProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void sendText(String content) {
        RestTemplate restTemplate = createRestTemplate(properties.getTimeoutMs());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("msgtype", "text");
        ObjectNode text = body.putObject("text");
        text.put("content", content);

        ResponseEntity<String> response = restTemplate.postForEntity(
                properties.getWebhookUrl(),
                new HttpEntity<String>(body.toString(), headers),
                String.class
        );
        assertSuccess(response.getBody());
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.getWebhookUrl());
    }

    private RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
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
            // Some proxies may not return JSON. HTTP success is enough in that case.
        }
    }
}
