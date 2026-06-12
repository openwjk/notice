package com.jkoi.notice.util;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 工厂，统一管理超时配置，避免各处重复创建。
 */
public final class RestTemplateFactory {

    private static final int DEFAULT_TIMEOUT_MS = 10000;

    private RestTemplateFactory() {
    }

    /**
     * 创建指定超时时间的 RestTemplate 实例。
     */
    public static RestTemplate create(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int safeTimeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        factory.setConnectTimeout(safeTimeout);
        factory.setReadTimeout(safeTimeout);
        return new RestTemplate(factory);
    }

    /**
     * 使用默认超时时间创建 RestTemplate 实例。
     */
    public static RestTemplate create() {
        return create(DEFAULT_TIMEOUT_MS);
    }
}
