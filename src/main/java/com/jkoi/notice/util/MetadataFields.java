package com.jkoi.notice.util;

import com.jkoi.notice.config.NoticeProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 元数据字段名常量集合，统一管理 ReminderConfig/GitHub payload 中的非业务字段名。
 * 用于在解析字段时过滤掉元数据，避免将 id、title 等字段当作业务数据字段处理。
 */
public final class MetadataFields {

    private static final Set<String> BASE_FIELDS = new HashSet<>(Arrays.asList(
            "id", "title", "type", "enabled", "deleted",
            "cron", "corn", "exeCode", "dataField", "testDate", "fields", "updatedAt"
    ));

    private MetadataFields() {
    }

    /**
     * 判断字段名是否为元数据字段（非业务字段）。
     */
    public static boolean isMetadata(String fieldName, NoticeProperties noticeProperties) {
        if (BASE_FIELDS.contains(fieldName)) {
            return true;
        }
        String cronField = noticeProperties != null ? noticeProperties.getCronField() : null;
        return StringUtils.hasText(cronField) && fieldName.equals(cronField);
    }

    /**
     * 判断字段名是否为元数据字段（不含自定义 cronField 的简化版本）。
     */
    public static boolean isMetadata(String fieldName) {
        return isMetadata(fieldName, null);
    }
}
