package com.jkoi.notice.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReminderConfig {

    private String id;
    private String title;
    private String type;
    private String cron;
    private String data;
    private String exeCode;
    private String dataField;
    private String testDate;
    private List<ReminderField> fields = new ArrayList<ReminderField>();
    private boolean enabled = true;
    private boolean deleted;
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getExeCode() {
        return exeCode;
    }

    public void setExeCode(String exeCode) {
        this.exeCode = exeCode;
    }

    public String getDataField() {
        return dataField;
    }

    public void setDataField(String dataField) {
        this.dataField = dataField;
    }

    public String getTestDate() {
        return testDate;
    }

    public void setTestDate(String testDate) {
        this.testDate = testDate;
    }

    public List<ReminderField> getFields() {
        return fields;
    }

    public void setFields(List<ReminderField> fields) {
        this.fields = fields;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
