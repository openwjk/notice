package com.jkoi.notice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderStatRecord {

    private String id;
    private String title;
    private String type;
    private String cron;
    private String exeCode;
    private String message;
    private String occurredAt;

    /**
     * 兼容6参数构造（occurredAt 默认为空字符串），保持与现有调用处一致。
     */
    public ReminderStatRecord(String id, String title, String type, String cron, String exeCode, String message) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.cron = cron;
        this.exeCode = exeCode;
        this.message = message;
        this.occurredAt = "";
    }
}
