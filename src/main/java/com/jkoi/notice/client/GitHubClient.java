package com.jkoi.notice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkoi.notice.config.GitHubProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class GitHubClient {

    private final GitHubProperties properties;
    private final WeComWebhookClient weComWebhookClient;

    public GitHubClient(GitHubProperties properties, WeComWebhookClient weComWebhookClient) {
        this.properties = properties;
        this.weComWebhookClient = weComWebhookClient;
    }

    public String fetch(int count) {
        String responseBody = "";
        try {
            RestTemplate restTemplate = createRestTemplate(properties.getTimeoutMs());
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", properties.getAccept());
            headers.set("X-GitHub-Api-Version", properties.getApiVersion());
            headers.set("User-Agent", "notice");
            headers.setBearerAuth(properties.getToken());

            ResponseEntity<String> response = restTemplate.exchange(
                    resolveApiUrl(),
                    HttpMethod.GET,
                    new HttpEntity<String>(headers),
                    String.class
            );
            responseBody = response.getBody() == null ? "" : response.getBody();
            if (StringUtils.hasText(properties.getFilePath())) {
                return decodeContentApiResponse(responseBody);
            }
        }catch (Exception e) {
            if(count<10)
                return fetch(count+1);
            else
                weComWebhookClient.sendText("Failed to fetch GitHub content. Please check your GitHub token and API URL.");
        }

        return responseBody;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.getToken()) && StringUtils.hasText(properties.getApiUrl());
    }

    private String resolveApiUrl() {
        String configuredUrl = properties.getApiUrl();
        try {
            URI uri = URI.create(configuredUrl);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                return configuredUrl;
            }

            String[] parts = uri.getPath().replaceFirst("^/", "").split("/");
            if (parts.length < 2) {
                return configuredUrl;
            }

            String owner = parts[0];
            String repo = parts[1].replaceFirst("\\.git$", "");
            return appendFilePath("https://api.github.com/repos/" + owner + "/" + repo);
        } catch (Exception ignored) {
            return appendFilePath(configuredUrl);
        }
    }

    private String appendFilePath(String apiUrl) {
        if (!StringUtils.hasText(properties.getFilePath()) || apiUrl.contains("/contents/")) {
            return apiUrl;
        }

        String normalizedBaseUrl = apiUrl.replaceFirst("/$", "");
        String normalizedPath = properties.getFilePath().replaceFirst("^/+", "");
        String result = normalizedBaseUrl + "/contents/" + encodePath(normalizedPath);
        if (StringUtils.hasText(properties.getRef())) {
            result = result + "?ref=" + urlEncode(properties.getRef());
        }
        return result;
    }

    private String encodePath(String path) {
        String[] segments = path.split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append("/");
            }
            builder.append(urlEncode(segments[i]));
        }
        return builder.toString();
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to encode GitHub path segment: " + value, ex);
        }
    }

    private String decodeContentApiResponse(String responseBody) {
        try {
            JsonNode node = new ObjectMapper().readTree(responseBody);
            JsonNode content = node.get("content");
            if (content == null || !StringUtils.hasText(content.asText())) {
                return responseBody;
            }

            String encoded = content.asText().replace("\n", "");
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return responseBody;
        }
    }

    private RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }
}
