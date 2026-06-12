package com.jkoi.notice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.service.ScheduledService;
import com.jkoi.notice.util.RestTemplateFactory;
import com.jkoi.notice.util.TextUtils;
import lombok.SneakyThrows;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class ScheduledBirthdayReminderImpl implements ScheduledService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledBirthdayReminderImpl.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String VERBAL_TRICK = "今天是%s生日，记得送上生日祝福哦~";
    private static final int MAX_RETRY_COUNT = 10;

    private final WeComWebhookClient weComWebhookClient;
    private final String calendarUrl;
    private final ObjectMapper objectMapper;

    public ScheduledBirthdayReminderImpl(WeComWebhookClient weComWebhookClient,
                                         @Value("${hk.lunardate-url}") String calendarUrl) {
        this.weComWebhookClient = weComWebhookClient;
        this.calendarUrl = calendarUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getCode() {
        return "BIRTHDAY_REMINDER";
    }

    @Override
    public String getName() {
        return "生日提醒";
    }

    @Override
    public String getSample() {
        return "{\"data\":[{\"name\":\"张三\",\"birthday\":\"1990-01-01\"}]}";
    }

    @Override
    public void execute(Date date, JsonNode node) {
        try {
            String message = buildMessage(date == null ? new Date() : date, node);
            if (StringUtils.hasText(message)) {
                weComWebhookClient.sendText(message);
            }
        } catch (Exception ex) {
            log.error("Failed to execute BIRTHDAY_REMINDER.", ex);
        }
    }

    private String buildMessage(Date date, JsonNode node) {
        LocalDate today = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        JsonNode data = node.get("data");
        if (data == null || !data.isArray()) {
            return null;
        }

        String todayStr = today.format(DATE_FORMAT);
        String lunarDate = getLunarDate(todayStr);
        List<String> names = new ArrayList<>();

        for (JsonNode item : data) {
            String name = item.path("name").asText("");
            if (name.isEmpty()) {
                continue;
            }
            String birthday = item.path("birthday").asText("");
            if (todayStr.equals(birthday) || (!lunarDate.isEmpty() && lunarDate.contains(birthday))) {
                names.add(name);
            }
        }

        return names.isEmpty() ? null : String.format(VERBAL_TRICK, String.join(",", names));
    }

    @SneakyThrows
    private String getLunarDate(String today) {
        String lunarDateResp = requestHkLunarDate(today, 0);
        if (lunarDateResp != null && !lunarDateResp.isEmpty()) {
            JsonNode root = objectMapper.readTree(lunarDateResp);
            JsonNode lunarDateNode = root.get("LunarDate");
            return lunarDateNode != null ? lunarDateNode.asText() : "";
        }
        return "";
    }

    private String requestHkLunarDate(String today, int retryCount) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "notice");
        headers.set("Accept", "text/calendar,text/plain,*/*");

        try {
            ResponseEntity<byte[]> response = RestTemplateFactory.create().exchange(
                    calendarUrl + today,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class
            );
            byte[] body = response.getBody();
            if (body != null && body.length > 0) {
                return new String(body, StandardCharsets.UTF_8);
            }
            log.warn("LunarDate '{}' returned empty content.", calendarUrl);
        } catch (ResourceAccessException ex) {
            log.warn("Failed to fetch lunarDate '{}'. Cause: {}",
                    calendarUrl, TextUtils.getRootCauseMessage(ex));
            if (retryCount < MAX_RETRY_COUNT) {
                return requestHkLunarDate(today, retryCount + 1);
            }
        }
        log.warn("LunarDate url failed. Please check DNS, proxy or calendarUrl.");
        return "";
    }
}
