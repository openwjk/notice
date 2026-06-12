package com.jkoi.notice.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ReminderStats {

    private String date;
    private int enabled;
    private int todayMatched;
    private int errors;
    private String lastMatchedAt;
    private String lastErrorAt;
    private String lastErrorMessage;
    private List<ReminderStatRecord> todayRecords = new ArrayList<>();
    private List<ReminderStatRecord> errorRecords = new ArrayList<>();
}
