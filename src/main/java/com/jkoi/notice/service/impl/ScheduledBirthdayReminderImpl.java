package com.jkoi.notice.service.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.service.ScheduledService;
import lombok.SneakyThrows;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * @author wangjunkai
 * @description
 * @date 2023/7/28 13:38
 */
@Service
public class ScheduledBirthdayReminderImpl implements ScheduledService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledBirthdayReminderImpl.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final String VERBAL_TRICK = "今天是%s生日，记得送上生日祝福哦~";

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
    public void execute(Date date, JsonNode node) {
        try {
            String message = buildMessage(date == null ? new Date() : date, node);
            if (StringUtils.hasText(message)) {
                weComWebhookClient.sendText(message);
            }
        } catch (Exception ex) {
            log.error("Failed to execute FESTIVAL_REMINDER.", ex);
        }
    }

    private String buildMessage(Date date, JsonNode node) {
        LocalDate today = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        JsonNode data = node.get("data");
        if (data == null || !data.isArray())
            return null;
        String toDayStr = today.format(DATE_FORMAT);
        String lunarDate = getLunarDate(toDayStr);
        List<String> names = new ArrayList<>();
        for (JsonNode item : data) {
            String birthDay = item.get("birthday").asText();
            String name = item.get("name").asText();
            if (name.isEmpty())
                continue;
            if (!toDayStr.isEmpty() && toDayStr.equals(birthDay)) {
                names.add(name);
            } else if (!lunarDate.isEmpty() && lunarDate.contains(birthDay)) {
                names.add(name);
            }
        }
        if (names.isEmpty())
            return null;
        return String.format(VERBAL_TRICK, String.join(",", names));
    }

    @SneakyThrows
    private String getLunarDate(String today) {
        int count = 0;
        String lunarDateResp = requestHkLunarDate(today, count);
        if (lunarDateResp != null && !lunarDateResp.isEmpty()) {
            JsonNode root = objectMapper.readTree(lunarDateResp);
            return root.get("LunarDate") != null ? root.get("LunarDate").asText() : "";
        }
        return "";
    }

    private String requestHkLunarDate(String today, int count) {
        RestTemplate restTemplate = createRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "notice");
        headers.set("Accept", "text/calendar,text/plain,*/*");
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    calendarUrl + today,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    byte[].class
            );
            byte[] body = response.getBody();
            if (body != null && body.length > 0) {
                return new String(body, StandardCharsets.UTF_8);
            }
            log.warn("LunarDate '{}' returned empty content.", calendarUrl);
        } catch (ResourceAccessException ex) {
            log.warn("Failed to fetch lunarDate '{}'. Cause: {}",
                    calendarUrl, getRootCauseMessage(ex));
            if (count < 10) {
                return requestHkLunarDate(today, ++count);
            }
        }
        log.warn("LunarDate url failed. Please check DNS, proxy or calendarUrl.");
        return "";
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    private String getRootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }
}
