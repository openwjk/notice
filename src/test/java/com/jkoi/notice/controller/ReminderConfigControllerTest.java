package com.jkoi.notice.controller;

import com.jkoi.notice.model.ReminderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "notice.enabled=false",
        "notice-web.storage-path=target/test-data/reminders-api.json",
        "notice-web.stats-storage-path=target/test-data/reminders-api-stats.json",
        "logging.file.name=target/test-data/system-test.log",
        "logging.logback.rollingpolicy.file-name-pattern=target/test-data/system-test.%d{yyyy-MM-dd}.log"
})
class ReminderConfigControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void returnsEmptyWhenWxIdHeaderIsMissing() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/reminders",
                HttpMethod.GET,
                new HttpEntity<Void>(new HttpHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void returnsEmptyForOptionsWhenWxIdHeaderIsMissing() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/reminders",
                HttpMethod.OPTIONS,
                new HttpEntity<Void>(new HttpHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void allowsRootRequestWithoutWxId() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("success");
    }

    @Test
    void rootResolvesDevWxIdWhenWechatSecretIsMissing() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/?code=test-code", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat((Map) response.getBody().get("data"))
                .extracting("wxid")
                .asString()
                .startsWith("dev-");
    }

    @Test
    void savesAndListsReminderWithWxId() {
        ReminderConfig reminderConfig = new ReminderConfig();
        reminderConfig.setId("test-reminder");
        reminderConfig.setTitle("接口测试提醒");
        reminderConfig.setType("text");
        reminderConfig.setCron("0 0 8 * * ?");
        reminderConfig.setData("测试消息");
        reminderConfig.setDataField("data");
        reminderConfig.setEnabled(true);

        ResponseEntity<Map> saveResponse = restTemplate.exchange(
                "/api/reminders",
                HttpMethod.POST,
                new HttpEntity<ReminderConfig>(reminderConfig, headers()),
                Map.class
        );
        ResponseEntity<Map> listResponse = restTemplate.exchange(
                "/api/reminders",
                HttpMethod.GET,
                new HttpEntity<Void>(headers()),
                Map.class
        );

        assertThat(saveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saveResponse.getBody()).containsEntry("success", true);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).containsEntry("success", true);
    }

    @Test
    void listsExecutionCodesWithWxId() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/reminders/exe-codes",
                HttpMethod.GET,
                new HttpEntity<Void>(headers()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody().get("data")).isInstanceOf(List.class);
        assertThat((List) response.getBody().get("data"))
                .extracting("code")
                .contains("TODAY_REMINDER", "BIRTHDAY_REMINDER", "FESTIVAL_REMINDER");
        assertThat((Map) ((List) response.getBody().get("data")).get(0))
                .containsKeys("code", "name", "title", "sample");
        Map birthday = (Map) ((List) response.getBody().get("data")).stream()
                .filter(item -> "BIRTHDAY_REMINDER".equals(((Map) item).get("code")))
                .findFirst()
                .orElse(null);
        assertThat(birthday).isNotNull();
        assertThat(birthday.get("sample")).asString().contains("\"data\"", "\"birthday\"");

        ResponseEntity<Map> sampleResponse = restTemplate.exchange(
                "/api/reminders/exe-codes/BIRTHDAY_REMINDER/sample",
                HttpMethod.GET,
                new HttpEntity<Void>(headers()),
                Map.class
        );
        assertThat(sampleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map) sampleResponse.getBody().get("data"))
                .containsEntry("code", "BIRTHDAY_REMINDER");
        assertThat(((Map) sampleResponse.getBody().get("data")).get("sample"))
                .asString()
                .contains("\"data\"", "\"birthday\"");
    }

    @Test
    void listsSystemLogsWithWxId() throws Exception {
        Path logDirectory = Paths.get("target/test-data");
        Files.createDirectories(logDirectory);
        Files.write(logDirectory.resolve("system-test.log"), Arrays.asList(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(logDirectory, "system-test.2030-*.log")) {
            for (Path path : stream) {
                Files.deleteIfExists(path);
            }
        }
        String stackMessage = "File sourced log message " + System.nanoTime();
        String rotatedMessage = "Rotated daily log message " + System.nanoTime();
        String laterMessage = "Later next day log message " + System.nanoTime();
        Path stackLogFile = logDirectory.resolve("system-test.2030-01-02.log");
        Files.write(stackLogFile, Arrays.asList(
                "2030-01-02 03:04:05.123  WARN 1234 --- [    test-thread] c.j.notice.TestLogger                   : " + stackMessage,
                "java.lang.IllegalStateException: sample stack",
                "\tat com.jkoi.notice.TestLogger.fail(TestLogger.java:12)",
                "Caused by: java.lang.RuntimeException: root cause",
                "2030-01-02 03:04:06.123  WARN 1234 --- [    test-thread] c.j.notice.TestLogger                   : " + laterMessage
        ), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Path rotatedLogFile = logDirectory.resolve("system-test.2030-01-01.log");
        Files.write(rotatedLogFile, Arrays.asList(
                "2030-01-01 11:12:13.456  WARN 1234 --- [ rotated-thread] c.j.notice.RotatedLogger                : " + rotatedMessage
        ), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/system/logs?limit=20&level=WARN&start=2030-01-01T00:00:00&end=2030-01-02T03:05:00",
                HttpMethod.GET,
                new HttpEntity<Void>(headers()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat((Map) response.getBody().get("data"))
                .containsKeys("entries", "cursor", "total", "serverTime", "start", "end", "source", "logFile");
        assertThat((List) ((Map) response.getBody().get("data")).get("entries"))
                .extracting("message")
                .contains(stackMessage, rotatedMessage, laterMessage);
        ResponseEntity<Map> firstPageResponse = restTemplate.exchange(
                "/api/system/logs?limit=2&level=WARN&start=2030-01-01T00:00:00&end=2030-01-02T03:05:00",
                HttpMethod.GET,
                new HttpEntity<Void>(headers()),
                Map.class
        );
        assertThat((List) ((Map) firstPageResponse.getBody().get("data")).get("entries"))
                .extracting("message")
                .containsExactly(rotatedMessage, stackMessage);
        List firstPageEntries = (List) ((Map) firstPageResponse.getBody().get("data")).get("entries");
        Long stackSequence = ((Number) ((Map) firstPageEntries.get(1)).get("sequence")).longValue();
        ResponseEntity<Map> previousPageResponse = restTemplate.exchange(
                "/api/system/logs?limit=2&level=WARN&start=2030-01-01T00:00:00&end=2030-01-02T03:05:00&before=" + stackSequence,
                HttpMethod.GET,
                new HttpEntity<Void>(headers()),
                Map.class
        );
        assertThat((List) ((Map) previousPageResponse.getBody().get("data")).get("entries"))
                .extracting("message")
                .containsExactly(rotatedMessage);
        assertThat((List) ((Map) response.getBody().get("data")).get("entries"))
                .filteredOn(entry -> stackMessage.equals(((Map) entry).get("message")))
                .extracting(entry -> ((Map) entry).get("throwable"))
                .first()
                .asString()
                .contains("java.lang.IllegalStateException", "TestLogger.java:12", "Caused by");
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("wxid", "test-openid");
        return headers;
    }

    private HttpHeaders wxHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("wxid", "test-openid");
        return headers;
    }
}
