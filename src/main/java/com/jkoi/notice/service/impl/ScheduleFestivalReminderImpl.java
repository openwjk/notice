package com.jkoi.notice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.service.ScheduledService;
import com.jkoi.notice.util.IcsCalendarParser;
import com.jkoi.notice.util.IcsCalendarParser.CalendarEvent;
import com.jkoi.notice.util.RestTemplateFactory;
import com.jkoi.notice.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * 读取 iCloud 中国节假日日历，在周末补班或工作日放假时发送提醒。
 */
@Service
public class ScheduleFestivalReminderImpl implements ScheduledService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleFestivalReminderImpl.class);
    private static final DateTimeFormatter MESSAGE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final WeComWebhookClient weComWebhookClient;
    private final String calendarUrl;
    private final int timeoutMs;

    public ScheduleFestivalReminderImpl(WeComWebhookClient weComWebhookClient,
                                        @Value("${festival.calendar-url:https://ical.muhan.org}") String calendarUrl,
                                        @Value("${festival.timeout-ms:10000}") int timeoutMs) {
        this.weComWebhookClient = weComWebhookClient;
        this.calendarUrl = calendarUrl;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String getCode() {
        return "FESTIVAL_REMINDER";
    }

    @Override
    public String getName() {
        return "节假日闹钟开闭提醒";
    }

    @Override
    public String getSample() {
        return "{}";
    }

    @Override
    public void execute(Date date, JsonNode node) {
        try {
            String message = buildMessage(date == null ? new Date() : date);
            if (StringUtils.hasText(message)) {
                weComWebhookClient.sendText(message);
            }
        } catch (Exception ex) {
            log.error("Failed to execute FESTIVAL_REMINDER.", ex);
        }
    }

    private String buildMessage(Date date) {
        LocalDate tomorrow = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(1);
        List<CalendarEvent> events = IcsCalendarParser.parseEvents(fetchCalendarContent());
        List<String> summaries = IcsCalendarParser.findSummaries(events, tomorrow);

        if (summaries.isEmpty()) {
            log.info("No festival calendar event found for {}.", tomorrow);
            return null;
        }

        String summaryText = String.join("、", summaries);
        DayOfWeek dayOfWeek = tomorrow.getDayOfWeek();
        boolean weekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

        if (IcsCalendarParser.containsWorkday(summaryText)) {
            return "明天是" + tomorrow.format(MESSAGE_DATE_FORMAT) + "，"
                    + IcsCalendarParser.formatWeekdayShort(dayOfWeek) + "，"
                    + summaryText + "，为补班日，记得调好闹钟哦~";
        }
        if (IcsCalendarParser.containsHoliday(summaryText) && !weekend) {
            return "明天是" + tomorrow.format(MESSAGE_DATE_FORMAT) + "，"
                    + IcsCalendarParser.formatWeekdayShort(dayOfWeek) + "，"
                    + summaryText + "，为假期休息日，记得关闭闹钟哦~";
        }
        return null;
    }

    private String fetchCalendarContent() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "notice");
        headers.set("Accept", "text/calendar,text/plain,*/*");

        try {
            ResponseEntity<byte[]> response = RestTemplateFactory.create(timeoutMs).exchange(
                    calendarUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class
            );
            byte[] body = response.getBody();
            if (body != null && body.length > 0) {
                return new String(body, StandardCharsets.UTF_8);
            }
            log.warn("Festival calendar '{}' returned empty content.", calendarUrl);
        } catch (ResourceAccessException ex) {
            log.warn("Failed to fetch festival calendar '{}'. Cause: {}",
                    calendarUrl, TextUtils.getRootCauseMessage(ex));
        }
        log.warn("Festival calendar url failed. Please check DNS, proxy or FESTIVAL_CALENDAR_URL.");
        return "";
    }
}
