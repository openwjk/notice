package com.jkoi.notice.model;

import java.util.ArrayList;
import java.util.List;

public class ReminderStats {

    private String date;
    private int enabled;
    private int todayMatched;
    private int errors;
    private String lastMatchedAt;
    private String lastErrorAt;
    private String lastErrorMessage;
    private List<ReminderStatRecord> todayRecords = new ArrayList<ReminderStatRecord>();
    private List<ReminderStatRecord> errorRecords = new ArrayList<ReminderStatRecord>();

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getEnabled() {
        return enabled;
    }

    public void setEnabled(int enabled) {
        this.enabled = enabled;
    }

    public int getTodayMatched() {
        return todayMatched;
    }

    public void setTodayMatched(int todayMatched) {
        this.todayMatched = todayMatched;
    }

    public int getErrors() {
        return errors;
    }

    public void setErrors(int errors) {
        this.errors = errors;
    }

    public String getLastMatchedAt() {
        return lastMatchedAt;
    }

    public void setLastMatchedAt(String lastMatchedAt) {
        this.lastMatchedAt = lastMatchedAt;
    }

    public String getLastErrorAt() {
        return lastErrorAt;
    }

    public void setLastErrorAt(String lastErrorAt) {
        this.lastErrorAt = lastErrorAt;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public List<ReminderStatRecord> getTodayRecords() {
        return todayRecords;
    }

    public void setTodayRecords(List<ReminderStatRecord> todayRecords) {
        this.todayRecords = todayRecords;
    }

    public List<ReminderStatRecord> getErrorRecords() {
        return errorRecords;
    }

    public void setErrorRecords(List<ReminderStatRecord> errorRecords) {
        this.errorRecords = errorRecords;
    }
}
