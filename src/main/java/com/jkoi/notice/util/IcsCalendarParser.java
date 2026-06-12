package com.jkoi.notice.util;

import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ICS 日历文件解析工具类，统一管理节假日日历的解析逻辑。
 * 被 ScheduleFestivalReminderImpl 和 ScheduleTodayReminderImpl 共用。
 */
public final class IcsCalendarParser {

    private static final DateTimeFormatter ICS_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private IcsCalendarParser() {
    }

    /**
     * 解析 ICS 格式日历内容，提取所有事件。
     */
    public static List<CalendarEvent> parseEvents(String content) {
        List<CalendarEvent> events = new ArrayList<>();
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

    /**
     * 在事件列表中查找指定日期的事件摘要。
     */
    public static List<String> findSummaries(List<CalendarEvent> events, LocalDate date) {
        List<String> summaries = new ArrayList<>();
        for (CalendarEvent event : events) {
            if (date.equals(event.getStartDate())) {
                summaries.add(event.getSummary());
            }
        }
        return summaries;
    }

    /**
     * 判断摘要文本是否包含工作日/补班标识。
     */
    public static boolean containsWorkday(String value) {
        return value.contains("班") || value.contains("调休上班") || value.contains("工作日");
    }

    /**
     * 判断摘要文本是否包含假期标识。
     */
    public static boolean containsHoliday(String value) {
        return value.contains("假") || value.contains("休") || value.contains("节");
    }

    /**
     * 格式化星期为中文短格式（周一 ~ 周日）。
     */
    public static String formatWeekdayShort(DayOfWeek dayOfWeek) {
        String[] names = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return names[dayOfWeek.getValue() - 1];
    }

    /**
     * 格式化星期为中文长格式（星期一 ~ 星期日）。
     */
    public static String formatWeekdayLong(DayOfWeek dayOfWeek) {
        String[] names = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        return names[dayOfWeek.getValue() - 1];
    }

    private static String unfoldLines(String content) {
        return content.replace("\n ", "").replace("\n\t", "");
    }

    private static LocalDate parseDateValue(String line) {
        String value = parseTextValue(line);
        if (!StringUtils.hasText(value) || value.length() < 8) {
            return null;
        }
        return LocalDate.parse(value.substring(0, 8), ICS_DATE_FORMAT);
    }

    private static String parseTextValue(String line) {
        int separator = line.indexOf(':');
        if (separator < 0 || separator == line.length() - 1) {
            return "";
        }
        return line.substring(separator + 1).trim();
    }

    private static String unescapeIcsText(String value) {
        return value.replace("\\n", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }

    /**
     * ICS 日历事件模型
     */
    public static class CalendarEvent {
        private final LocalDate startDate;
        private final String summary;

        public CalendarEvent(LocalDate startDate, String summary) {
            this.startDate = startDate;
            this.summary = summary;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public String getSummary() {
            return summary;
        }
    }
}
