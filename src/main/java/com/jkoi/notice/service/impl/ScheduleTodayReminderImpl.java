package com.jkoi.notice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ScheduleTodayReminderImpl implements ScheduledService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTodayReminderImpl.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String TENCENT_WEATHER_PATH = "/ws/weather/v1/";
    private static final String TENCENT_WEATHER_URL = "https://apis.map.qq.com" + TENCENT_WEATHER_PATH;

    private final WeComWebhookClient weComWebhookClient;
    private final ObjectMapper objectMapper;
    private final String tencentKey;
    private final String tencentSecretKey;
    private final String tencentAdcode;
    private final int timeoutMs;
    private final String festivalCalendarUrl;
    private final int festivalTimeoutMs;

    public ScheduleTodayReminderImpl(WeComWebhookClient weComWebhookClient,
                                     ObjectMapper objectMapper,
                                     @Value("${tencent.key:}") String tencentKey,
                                     @Value("${tencent.secret-key:}") String tencentSecretKey,
                                     @Value("${tencent.adcode:310115}") String tencentAdcode,
                                     @Value("${tencent.timeout-ms:10000}") int timeoutMs,
                                     @Value("${festival.calendar-url:https://ical.muhan.org}") String festivalCalendarUrl,
                                     @Value("${festival.timeout-ms:10000}") int festivalTimeoutMs) {
        this.weComWebhookClient = weComWebhookClient;
        this.objectMapper = objectMapper;
        this.tencentKey = tencentKey;
        this.tencentSecretKey = tencentSecretKey;
        this.tencentAdcode = tencentAdcode;
        this.timeoutMs = timeoutMs;
        this.festivalCalendarUrl = festivalCalendarUrl;
        this.festivalTimeoutMs = festivalTimeoutMs;
    }

    @Override
    public String getCode() {
        return "TODAY_REMINDER";
    }

    @Override
    public String getName() {
        return "今日提醒";
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
            log.error("Failed to execute TODAY_REMINDER.", ex);
        }
    }

    // ======================== 消息构建 ========================

    private String buildMessage(Date date) {
        LocalDate today = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        StringBuilder builder = new StringBuilder();
        builder.append("今天: ").append(today.format(DATE_FORMAT)).append("\n");
        builder.append("星期: ").append(IcsCalendarParser.formatWeekdayLong(today.getDayOfWeek()));

        String weather = fetchTencentLiveWeather();
        if (StringUtils.hasText(weather)) {
            builder.append("\n").append(weather);
        }

        String holidayTip = buildNearestHolidayTip(today);
        if (StringUtils.hasText(holidayTip)) {
            builder.append("\n").append(holidayTip);
        }
        return builder.toString();
    }

    private String buildNearestHolidayTip(LocalDate today) {
        java.util.List<CalendarEvent> events = IcsCalendarParser.parseEvents(fetchCalendarContent());
        if (events.isEmpty()) {
            return null;
        }

        CalendarEvent nearest = null;
        for (CalendarEvent event : events) {
            if (!IcsCalendarParser.containsHoliday(event.getSummary())) {
                continue;
            }
            if (event.getStartDate().isBefore(today)) {
                continue;
            }
            if (nearest == null || event.getStartDate().isBefore(nearest.getStartDate())) {
                nearest = event;
            }
        }

        if (nearest == null || today.equals(nearest.getStartDate())) {
            return null;
        }

        long days = ChronoUnit.DAYS.between(today, nearest.getStartDate());
        String name = normalizeHolidayName(nearest.getSummary());
        return "距离" + (StringUtils.hasText(name) ? name : "节假日") + "还有" + days + "天";
    }

    private String normalizeHolidayName(String summary) {
        if (!StringUtils.hasText(summary)) {
            return "";
        }
        return summary.trim()
                .replace("假期", "").replace("放假", "")
                .replace("调休", "").replace("补班", "")
                .trim();
    }

    // ======================== 天气接口 ========================

    private String fetchTencentLiveWeather() {
        if (!StringUtils.hasText(tencentKey)) {
            log.warn("Missing TENCENT_KEY, skip Tencent weather query.");
            return "天气: 未配置腾讯位置服务 Key";
        }
        if (!StringUtils.hasText(tencentSecretKey)) {
            log.warn("Missing TENCENT_SECRET_KEY, skip Tencent weather query.");
            return "天气: 未配置腾讯位置服务 Secret Key";
        }
        if (!StringUtils.hasText(tencentAdcode)) {
            log.warn("Missing TENCENT_ADCODE, skip Tencent weather query.");
            return "天气: 未配置腾讯天气 adcode";
        }

        try {
            String url = buildTencentWeatherUrl();
            ResponseEntity<String> response = RestTemplateFactory.create(timeoutMs).getForEntity(url, String.class);
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                return "天气: 腾讯天气接口返回为空";
            }
            return parseTencentLiveWeather(body);
        } catch (ResourceAccessException ex) {
            log.warn("Failed to fetch Tencent weather. Cause: {}", TextUtils.getRootCauseMessage(ex));
            return "天气: 腾讯天气接口访问失败";
        } catch (Exception ex) {
            log.warn("Failed to parse Tencent weather response.", ex);
            return "天气: 腾讯天气接口解析失败";
        }
    }

    private String buildTencentWeatherUrl() {
        Map<String, String> params = new TreeMap<>();
        params.put("adcode", tencentAdcode);
        params.put("key", tencentKey);

        String rawQuery = buildQuery(params, false);
        String encodedQuery = buildQuery(params, true);
        String sig = buildTencentSig(rawQuery);
        return TENCENT_WEATHER_URL + "?" + encodedQuery + "&sig=" + sig;
    }

    private String buildTencentSig(String rawQuery) {
        String plain = TENCENT_WEATHER_PATH + "?" + rawQuery + tencentSecretKey;
        return DigestUtils.md5DigestAsHex(plain.getBytes(StandardCharsets.UTF_8));
    }

    private String buildQuery(Map<String, String> params, boolean encodeValue) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append("&");
            }
            builder.append(entry.getKey()).append("=");
            String value = entry.getValue() == null ? "" : entry.getValue();
            builder.append(encodeValue ? urlEncode(value) : value);
            first = false;
        }
        return builder.toString();
    }

    private String parseTencentLiveWeather(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (root.path("status").asInt(-1) != 0) {
            String message = TextUtils.firstNonBlank(
                    root.path("message").asText(""),
                    root.path("msg").asText(""),
                    root.path("info").asText(""),
                    "未知错误"
            );
            return "天气: 腾讯天气接口返回失败 - " + message;
        }

        JsonNode realtime = root.path("result").path("realtime");
        if (!realtime.isArray() || realtime.size() == 0) {
            return "天气: 腾讯天气接口未返回实时天气";
        }

        JsonNode live = realtime.get(0);
        JsonNode infos = live.path("infos");
        if (infos.isMissingNode() || infos.isNull() || !infos.isObject()) {
            return "天气: 腾讯天气接口未返回实时天气详情";
        }

        String district = TextUtils.firstText(live, "district", "city", "province");
        String weather = TextUtils.textOf(infos, "weather");
        String temperature = TextUtils.textOf(infos, "temperature");
        String humidity = TextUtils.textOf(infos, "humidity");
        String windDirection = TextUtils.textOf(infos, "wind_direction");
        String windPower = TextUtils.firstText(infos, "wind_power_v2", "wind_power");

        StringBuilder builder = new StringBuilder("天气: ");
        appendWithSpace(builder, district);
        appendWithSpace(builder, weather);
        if (StringUtils.hasText(temperature)) {
            builder.append(temperature).append("°C ");
        }
        if (StringUtils.hasText(humidity)) {
            builder.append("湿度").append(humidity).append("% ");
        }
        if (StringUtils.hasText(windDirection) || StringUtils.hasText(windPower)) {
            builder.append("风力");
            appendWithSpace(builder, windDirection);
            appendWithSpace(builder, windPower);
        }
        return builder.toString().trim();
    }

    // ======================== 日历获取 ========================

    private String fetchCalendarContent() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "notice");
        headers.set("Accept", "text/calendar,text/plain,*/*");

        try {
            ResponseEntity<byte[]> response = RestTemplateFactory.create(festivalTimeoutMs).exchange(
                    festivalCalendarUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class
            );
            byte[] body = response.getBody();
            if (body != null && body.length > 0) {
                return new String(body, StandardCharsets.UTF_8);
            }
            log.warn("Festival calendar '{}' returned empty content.", festivalCalendarUrl);
        } catch (ResourceAccessException ex) {
            log.warn("Failed to fetch festival calendar '{}'. Cause: {}",
                    festivalCalendarUrl, TextUtils.getRootCauseMessage(ex));
        }
        return "";
    }

    // ======================== 辅助方法 ========================

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to encode Tencent query value.", ex);
        }
    }

    private void appendWithSpace(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(value).append(" ");
        }
    }
}
