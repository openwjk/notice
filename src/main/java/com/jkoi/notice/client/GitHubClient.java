package com.jkoi.notice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jkoi.notice.config.GitHubProperties;
import com.jkoi.notice.util.RestTemplateFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class GitHubClient {

    private static final int MAX_RETRY_COUNT = 10;

    private final GitHubProperties properties;
    private final WeComWebhookClient weComWebhookClient;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public GitHubClient(GitHubProperties properties,
                        WeComWebhookClient weComWebhookClient,
                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.weComWebhookClient = weComWebhookClient;
        this.objectMapper = objectMapper;
        this.restTemplate = RestTemplateFactory.create(properties.getTimeoutMs());
    }

    /**
     * 带重试的内容获取，最多重试 MAX_RETRY_COUNT 次。
     */
    public String fetch(int count) {
        try {
            return fetchContent().getContent();
        } catch (Exception e) {
            if (count < MAX_RETRY_COUNT) {
                return fetch(count + 1);
            }
            weComWebhookClient.sendText("Failed to fetch GitHub content. Please check your GitHub token and API URL.");
        }
        return "";
    }

    public GitHubFileContent fetchContent() {
        return fetchContent(properties.getFilePath());
    }

    public GitHubFileContent fetchContent(String filePath) {
        String responseBody = fetchRawContentApiResponse(resolveApiUrl(true, filePath));
        return decodeContentApiResponse(responseBody);
    }

    public void save(String content) {
        save(properties.getFilePath(), content);
    }

    public void save(String filePath, String content) {
        try {
            String sha = fetchCurrentSha(filePath);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("message", "Update notice reminder config");
            body.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
            if (StringUtils.hasText(sha)) {
                body.put("sha", sha);
            }
            if (StringUtils.hasText(properties.getRef())) {
                body.put("branch", properties.getRef());
            }

            restTemplate.exchange(
                    resolveApiUrl(false, filePath),
                    HttpMethod.PUT,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), buildHeaders(MediaType.APPLICATION_JSON_VALUE)),
                    String.class
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save GitHub content.", ex);
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.getToken()) && StringUtils.hasText(properties.getApiUrl());
    }

    // ======================== 内部方法 ========================

    private String fetchCurrentSha(String filePath) {
        try {
            return fetchContent(filePath).getSha();
        } catch (HttpClientErrorException.NotFound ex) {
            return "";
        }
    }

    private String fetchRawContentApiResponse(String url) {
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders(null)),
                String.class
        );
        return response.getBody() == null ? "" : response.getBody();
    }

    private HttpHeaders buildHeaders(String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", properties.getAccept());
        headers.set("X-GitHub-Api-Version", properties.getApiVersion());
        headers.set("User-Agent", "notice");
        if (StringUtils.hasText(properties.getToken())) {
            headers.setBearerAuth(properties.getToken());
        }
        if (StringUtils.hasText(contentType)) {
            headers.set("Content-Type", contentType);
        }
        return headers;
    }

    private String resolveApiUrl(boolean includeRef, String filePath) {
        String configuredUrl = properties.getApiUrl();
        try {
            URI uri = URI.create(configuredUrl);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                return appendFilePath(configuredUrl, includeRef, filePath);
            }

            String[] parts = uri.getPath().replaceFirst("^/", "").split("/");
            if (parts.length < 2) {
                return appendFilePath(configuredUrl, includeRef, filePath);
            }

            String owner = parts[0];
            String repo = parts[1].replaceFirst("\\.git$", "");
            return appendFilePath("https://api.github.com/repos/" + owner + "/" + repo, includeRef, filePath);
        } catch (Exception ignored) {
            return appendFilePath(configuredUrl, includeRef, filePath);
        }
    }

    private String appendFilePath(String apiUrl, boolean includeRef, String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return includeRef ? appendRef(apiUrl) : apiUrl.replaceFirst("\\?.*$", "");
        }

        String normalizedBaseUrl = apiUrl.replaceFirst("/$", "");
        String normalizedPath = filePath.replaceFirst("^/+", "");
        int contentsIndex = normalizedBaseUrl.indexOf("/contents/");
        if (contentsIndex >= 0) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, contentsIndex);
        }
        String result = normalizedBaseUrl + "/contents/" + encodePath(normalizedPath);
        return includeRef ? appendRef(result) : result;
    }

    private String appendRef(String url) {
        if (!StringUtils.hasText(properties.getRef()) || url.contains("?ref=")) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "ref=" + urlEncode(properties.getRef());
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

    private GitHubFileContent decodeContentApiResponse(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            JsonNode content = node.get("content");
            if (content == null || !StringUtils.hasText(content.asText())) {
                return new GitHubFileContent(responseBody, "");
            }

            String encoded = content.asText().replace("\n", "");
            byte[] decoded = Base64.getDecoder().decode(encoded);
            JsonNode shaNode = node.get("sha");
            String sha = shaNode == null || shaNode.isNull() ? "" : shaNode.asText();
            return new GitHubFileContent(new String(decoded, StandardCharsets.UTF_8), sha);
        } catch (Exception ignored) {
            return new GitHubFileContent(responseBody, "");
        }
    }

    // ======================== 内部类 ========================

    public static class GitHubFileContent {
        private final String content;
        private final String sha;

        public GitHubFileContent(String content, String sha) {
            this.content = content;
            this.sha = sha;
        }

        public String getContent() {
            return content;
        }

        public String getSha() {
            return sha;
        }
    }
}
