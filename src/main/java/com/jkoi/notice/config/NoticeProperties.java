package com.jkoi.notice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notice")
public class NoticeProperties {

    private boolean enabled;
    private long fixedDelayMs;
    private long initialDelayMs;
    private String cronField;
    private String dataField;
    private boolean onChangeOnly;
    private int maxContentLength;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public String getCronField() {
        return cronField;
    }

    public void setCronField(String cronField) {
        this.cronField = cronField;
    }

    public String getDataField() {
        return dataField;
    }

    public void setDataField(String dataField) {
        this.dataField = dataField;
    }

    public boolean isOnChangeOnly() {
        return onChangeOnly;
    }

    public void setOnChangeOnly(boolean onChangeOnly) {
        this.onChangeOnly = onChangeOnly;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }
}
