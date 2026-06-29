package com.jkoi.notice.controller;

import com.jkoi.notice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

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

    @Autowired
    private AuthService authService;

    private String testToken;

    @BeforeEach
    void setUp() {
        testToken = authService.issueTokenDirectly();
    }

    @Test
    void rejectsRequestWithoutToken() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/reminders",
                HttpMethod.GET,
                new HttpEntity<Void>(new HttpHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void allowsAuthCodeEndpointWithoutToken() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/code",
                HttpMethod.POST,
                new HttpEntity<Void>(new HttpHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void allowsAuthLoginEndpointWithoutToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"000000\"}", headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void allowsHealthEndpointWithoutToken() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/health",
                HttpMethod.GET,
                new HttpEntity<Void>(new HttpHeaders()),
                Map.class
        );

        // health 端点也需要 Token
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + testToken);
        return headers;
    }
}
