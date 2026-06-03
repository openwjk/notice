package com.jkoi.notice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.config.WeComProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleFestivalReminderImplTest {

    private static final String REAL_CALENDAR_URL = "https://ical.muhan.org";
    private static final int TIMEOUT_MS = 10000;

    @Test
    void getCodeReturnsFestivalReminder() {
        ScheduleFestivalReminderImpl reminder = new ScheduleFestivalReminderImpl(
                new RecordingWeComWebhookClient(),
                REAL_CALENDAR_URL,
                TIMEOUT_MS
        );

        assertEquals("FESTIVAL_REMINDER", reminder.getCode());
    }

    @Test
    void executeSendsHolidayReminderWhenTomorrowIsWeekdayHolidayFromRealCalendar() {
        RecordingWeComWebhookClient weComWebhookClient = new RecordingWeComWebhookClient();
        ScheduleFestivalReminderImpl reminder = new ScheduleFestivalReminderImpl(
                weComWebhookClient,
                REAL_CALENDAR_URL,
                TIMEOUT_MS
        );

        reminder.execute(date("2026-06-18"));

        assertEquals(1, weComWebhookClient.sentTexts.size());
        assertEquals("明天是2026-06-19，周五，端午节假期，为假期休息日，记得关闭闹钟哦~", weComWebhookClient.sentTexts.get(0));
    }

    @Test
    void executeSendsWorkdayReminderWhenTomorrowIsWeekendWorkdayFromRealCalendar() {
        RecordingWeComWebhookClient weComWebhookClient = new RecordingWeComWebhookClient();
        ScheduleFestivalReminderImpl reminder = new ScheduleFestivalReminderImpl(
                weComWebhookClient,
                REAL_CALENDAR_URL,
                TIMEOUT_MS
        );

        reminder.execute(date("2026-01-03"));

        assertEquals(1, weComWebhookClient.sentTexts.size());
        assertEquals("明天是2026-01-04，周日，元旦补班，为补班日，记得调好闹钟哦~", weComWebhookClient.sentTexts.get(0));
    }

    @Test
    void executeDoesNotSendWhenTomorrowIsWeekendHolidayFromRealCalendar() {
        RecordingWeComWebhookClient weComWebhookClient = new RecordingWeComWebhookClient();
        ScheduleFestivalReminderImpl reminder = new ScheduleFestivalReminderImpl(
                weComWebhookClient,
                REAL_CALENDAR_URL,
                TIMEOUT_MS
        );

        reminder.execute(date("2026-06-19"));

        assertTrue(weComWebhookClient.sentTexts.isEmpty());
    }

    @Test
    void executeDoesNotSendWhenTomorrowHasNoCalendarEventFromRealCalendar() {
        RecordingWeComWebhookClient weComWebhookClient = new RecordingWeComWebhookClient();
        ScheduleFestivalReminderImpl reminder = new ScheduleFestivalReminderImpl(
                weComWebhookClient,
                REAL_CALENDAR_URL,
                TIMEOUT_MS
        );

        reminder.execute(date("2026-06-17"));

        assertTrue(weComWebhookClient.sentTexts.isEmpty());
    }

    @Test
    void executeDoesNotSendWhenRealNetworkUrlsCannotBeReached() {
        RecordingWeComWebhookClient weComWebhookClient = new RecordingWeComWebhookClient();
        ScheduleFestivalReminderImpl reminder = new ScheduleFestivalReminderImpl(
                weComWebhookClient,
                "https://calendar.invalid.example",
                1000
        );

        reminder.execute(date("2026-06-18"));

        assertTrue(weComWebhookClient.sentTexts.isEmpty());
    }

    private Date date(String value) {
        return Date.from(LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static class RecordingWeComWebhookClient extends WeComWebhookClient {
        private final List<String> sentTexts = new ArrayList<String>();

        private RecordingWeComWebhookClient() {
            super(new WeComProperties(), new ObjectMapper());
        }

        @Override
        public void sendText(String content) {
            assertFalse(content == null || content.trim().isEmpty());
            sentTexts.add(content);
        }
    }
}
