package com.jkoi.notice.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ReminderConfig {

    private String id;
    private String title;
    private String type;
    private String cron;
    private String data;
    private String exeCode;
    private String dataField;
    private String testDate;
    private List<ReminderField> fields = new ArrayList<>();
    private boolean enabled = true;
    private boolean deleted;
    private LocalDateTime updatedAt;
}
