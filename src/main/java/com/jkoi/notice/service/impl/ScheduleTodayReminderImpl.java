package com.jkoi.notice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.service.ScheduledService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Service
public class ScheduleTodayReminderImpl implements ScheduledService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTodayReminderImpl.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String AMAP_WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo";

    private final WeComWebhookClient weComWebhookClient;
    private final ObjectMapper objectMapper;
    private final String amapKey;
    private final String amapCity;
    private final int timeoutMs;

    public ScheduleTodayReminderImpl(WeComWebhookClient weComWebhookClient,
                                     ObjectMapper objectMapper,
                                     @Value("${amap.key:}") String amapKey,
                                     @Value("${amap.city:310000}") String amapCity,
                                     @Value("${amap.timeout-ms:10000}") int timeoutMs) {
        this.weComWebhookClient = weComWebhookClient;
        this.objectMapper = objectMapper;
        this.amapKey = amapKey;
        this.amapCity = amapCity;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String getCode() {
        return "TODAY_REMINDER";
    }

    @Override
    public void execute(Date date) {
        try {
            String message = buildMessage(date == null ? new Date() : date);
            if (StringUtils.hasText(message)) {
                weComWebhookClient.sendText(message);
            }
        } catch (Exception ex) {
            log.error("Failed to execute TODAY_REMINDER.", ex);
        }
    }

    private String buildMessage(Date date) {
        LocalDate today = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        StringBuilder builder = new StringBuilder();
        builder.append("今天: ").append(today.format(DATE_FORMAT)).append("\n");
        builder.append("星期: ").append(formatWeekday(today.getDayOfWeek()));

        String weather = fetchAmapLiveWeather();
        if (StringUtils.hasText(weather)) {
            builder.append("\n").append(weather);
        }
        return builder.toString();
    }

    private String fetchAmapLiveWeather() {
        if (!StringUtils.hasText(amapKey)) {
            log.warn("Missing AMAP_KEY, skip AMap weather query.");
            return "天气: 未配置高德 API Key";
        }

        try {
            String url = AMAP_WEATHER_URL
                    + "?key=" + urlEncode(amapKey)
                    + "&city=" + urlEncode(amapCity)
                    + "&extensions=base";
            ResponseEntity<String> response = createRestTemplate().getForEntity(url, String.class);
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                return "天气: 高德接口返回为空";
            }
            return parseAmapLiveWeather(body);
        } catch (ResourceAccessException ex) {
            log.warn("Failed to fetch AMap weather. Cause: {}", getRootCauseMessage(ex));
            return "天气: 高德接口访问失败";
        } catch (Exception ex) {
            log.warn("Failed to parse AMap weather response.", ex);
            return "天气: 高德接口解析失败";
        }
    }

    private String parseAmapLiveWeather(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            String info = root.path("info").asText("未知错误");
            return "天气: 高德接口返回失败 - " + info;
        }

        JsonNode lives = root.path("lives");
        if (!lives.isArray() || lives.size() == 0) {
            return "天气: 高德接口未返回实时天气";
        }

        JsonNode live = lives.get(0);
        String province = live.path("province").asText("");
        String city = live.path("city").asText("");
        String weather = live.path("weather").asText("");
        String temperature = live.path("temperature").asText("");
        String windDirection = live.path("winddirection").asText("");
        String windPower = live.path("windpower").asText("");
        String humidity = live.path("humidity").asText("");
        String reportTime = live.path("reporttime").asText("");

        StringBuilder builder = new StringBuilder("天气: ");
//        appendWithSpace(builder, province);
//        appendWithSpace(builder, city);
        appendWithSpace(builder, weather);
        if (StringUtils.hasText(temperature)) {
            builder.append(temperature).append("°C ");
        }
        if (StringUtils.hasText(humidity)) {
            builder.append("湿度").append(humidity).append("% ");
        }
        if (StringUtils.hasText(windDirection) || StringUtils.hasText(windPower)) {
            builder.append("风力");
            if (StringUtils.hasText(windDirection)) {
                builder.append(windDirection).append("风");
            }
            if (StringUtils.hasText(windPower)) {
                builder.append(windPower).append("级");
            }
            builder.append(" ");
        }
//        if (StringUtils.hasText(reportTime)) {
//            builder.append("发布时间: ").append(reportTime);
//        }
        return builder.toString().trim();
    }

    private void appendWithSpace(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(value).append(" ");
        }
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to encode AMap query value.", ex);
        }
    }

    private String getRootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private String formatWeekday(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                return "星期一";
            case TUESDAY:
                return "星期二";
            case WEDNESDAY:
                return "星期三";
            case THURSDAY:
                return "星期四";
            case FRIDAY:
                return "星期五";
            case SATURDAY:
                return "星期六";
            case SUNDAY:
            default:
                return "星期日";
        }
    }
}
