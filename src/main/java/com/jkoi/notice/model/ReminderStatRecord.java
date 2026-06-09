package com.jkoi.notice.model;

public class ReminderStatRecord {

    private String id;
    private String title;
    private String type;
    private String cron;
    private String exeCode;
    private String message;
    private String occurredAt;

    public ReminderStatRecord() {
    }

    public ReminderStatRecord(String id, String title, String type, String cron, String exeCode, String message) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.cron = cron;
        this.exeCode = exeCode;
        this.message = message;
    }

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

    public String getExeCode() {
        return exeCode;
    }

    public void setExeCode(String exeCode) {
        this.exeCode = exeCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }
}
