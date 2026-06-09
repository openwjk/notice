package com.jkoi.notice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.service.ScheduledService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Reads the iCloud China holidays calendar and reminds when tomorrow is a
 * workday on weekend or a holiday on weekday.
 */
@Service
public class ScheduleFestivalReminderImpl implements ScheduledService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleFestivalReminderImpl.class);
    private static final DateTimeFormatter ICS_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
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
        List<CalendarEvent> events = parseEvents(fetchCalendarContent());
        List<String> summaries = findSummaries(events, tomorrow);
        if (summaries.isEmpty()) {
            log.info("No festival calendar event found for {}.", tomorrow);
            return null;
        }

        String summaryText = String.join("、", summaries);
        DayOfWeek dayOfWeek = tomorrow.getDayOfWeek();
        boolean weekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        if (containsWorkday(summaryText)) {
            return "明天是" + tomorrow.format(MESSAGE_DATE_FORMAT) + "，" + formatWeekday(dayOfWeek)
                    + "，" + summaryText + "，为补班日，记得调好闹钟哦~";
        }
        if (containsHoliday(summaryText) && !weekend) {
            return "明天是" + tomorrow.format(MESSAGE_DATE_FORMAT) + "，" + formatWeekday(dayOfWeek)
                    + "，" + summaryText + "，为假期休息日，记得关闭闹钟哦~";
        }
        return null;
    }

    private String fetchCalendarContent() {
        RestTemplate restTemplate = createRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "notice");
        headers.set("Accept", "text/calendar,text/plain,*/*");
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    calendarUrl,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    byte[].class
            );
            byte[] body = response.getBody();
            if (body != null && body.length > 0) {
                return new String(body, StandardCharsets.UTF_8);
            }
            log.warn("Festival calendar '{}' returned empty content.", calendarUrl);
        } catch (ResourceAccessException ex) {
            log.warn("Failed to fetch festival calendar '{}'. Cause: {}",
                    calendarUrl, getRootCauseMessage(ex));
        }
        log.warn("Festival calendar url failed. Please check DNS, proxy or FESTIVAL_CALENDAR_URL.");
        return "";
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    private String getRootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private List<CalendarEvent> parseEvents(String content) {
        List<CalendarEvent> events = new ArrayList<CalendarEvent>();
        if (!StringUtils.hasText(content)) {
            return events;
        }

        String normalized = unfoldLines(content.replace("\r\n", "\n").replace("\r", "\n"));
        String[] lines = normalized.split("\n");
        LocalDate startDate = null;
        String summary = null;
        boolean inEvent = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if ("BEGIN:VEVENT".equals(line)) {
                inEvent = true;
                startDate = null;
                summary = null;
                continue;
            }
            if ("END:VEVENT".equals(line)) {
                if (inEvent && startDate != null && StringUtils.hasText(summary)) {
                    events.add(new CalendarEvent(startDate, unescapeIcsText(summary)));
                }
                inEvent = false;
                continue;
            }
            if (!inEvent) {
                continue;
            }

            if (line.startsWith("DTSTART")) {
                startDate = parseDateValue(line);
            } else if (line.startsWith("SUMMARY")) {
                summary = parseTextValue(line);
            }
        }
        return events;
    }

    private String unfoldLines(String content) {
        return content.replace("\n ", "").replace("\n\t", "");
    }

    private LocalDate parseDateValue(String line) {
        String value = parseTextValue(line);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() >= 8) {
            return LocalDate.parse(value.substring(0, 8), ICS_DATE_FORMAT);
        }
        return null;
    }

    private String parseTextValue(String line) {
        int separator = line.indexOf(':');
        if (separator < 0 || separator == line.length() - 1) {
            return "";
        }
        return line.substring(separator + 1).trim();
    }

    private List<String> findSummaries(List<CalendarEvent> events, LocalDate date) {
        List<String> summaries = new ArrayList<String>();
        for (CalendarEvent event : events) {
            if (date.equals(event.getStartDate())) {
                summaries.add(event.getSummary());
            }
        }
        return summaries;
    }

    private boolean containsWorkday(String value) {
        return value.contains("班") || value.contains("调休上班") || value.contains("工作日");
    }

    private boolean containsHoliday(String value) {
        return value.contains("休") || value.contains("假") || value.contains("节");
    }

    private String formatWeekday(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                return "周一";
            case TUESDAY:
                return "周二";
            case WEDNESDAY:
                return "周三";
            case THURSDAY:
                return "周四";
            case FRIDAY:
                return "周五";
            case SATURDAY:
                return "周六";
            case SUNDAY:
            default:
                return "周日";
        }
    }

    private String unescapeIcsText(String value) {
        return value.replace("\\n", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }

    private static class CalendarEvent {
        private final LocalDate startDate;
        private final String summary;

        private CalendarEvent(LocalDate startDate, String summary) {
            this.startDate = startDate;
            this.summary = summary;
        }

        private LocalDate getStartDate() {
            return startDate;
        }

        private String getSummary() {
            return summary;
        }
    }
}
