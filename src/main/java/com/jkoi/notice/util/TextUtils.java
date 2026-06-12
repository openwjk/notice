package com.jkoi.notice.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

/**
 * 文本处理工具类，统一管理 JSON 文本提取、截断等公共逻辑。
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * 安全 trim，null 返回空字符串。
     */
    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 如果值为空则返回默认值。
     */
    public static String defaultText(String value, String defaultValue) {
        String trimmed = trim(value);
        return StringUtils.hasText(trimmed) ? trimmed : defaultValue;
    }

    /**
     * 截断字符串到指定长度。
     */
    public static String truncate(String value, int maxLength) {
        String text = trim(value);
        if (maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /**
     * 从 JsonNode 中提取文本字段值。
     */
    public static String textOf(JsonNode item, String fieldName) {
        if (item == null || !StringUtils.hasText(fieldName)) {
            return "";
        }
        JsonNode value = item.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    /**
     * 按优先级从 JsonNode 中提取第一个非空文本字段值。
     */
    public static String firstText(JsonNode item, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = textOf(item, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * 将 JsonNode 转为文本表示。字符串类型直接返回文本，其他类型序列化为 JSON。
     */
    public static String jsonValueToText(JsonNode value) throws Exception {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual()) {
            return value.asText();
        }
        return value.toString();
    }

    /**
     * 获取异常根因的消息摘要。
     */
    public static String getRootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    /**
     * 获取异常的简短消息（优先使用 message，否则使用类名）。
     */
    public static String shortMessage(Exception ex) {
        if (ex == null) {
            return "";
        }
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    /**
     * 返回第一个非空字符串，全部为空则返回空字符串。
     */
    public static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
