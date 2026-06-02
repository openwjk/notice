package com.jkoi.notice.task;

import com.jkoi.notice.client.GitHubClient;
import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.config.NoticeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkoi.notice.service.ScheduledFactory;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Component
public class GitHubNoticeScheduler {

    private static final Logger log = LoggerFactory.getLogger(GitHubNoticeScheduler.class);

    private final GitHubClient gitHubClient;
    private final WeComWebhookClient weComWebhookClient;
    private final NoticeProperties noticeProperties;
    private final ObjectMapper objectMapper;
    private final ScheduledFactory scheduledFactory;

    public GitHubNoticeScheduler(GitHubClient gitHubClient,
                                 WeComWebhookClient weComWebhookClient,
                                 NoticeProperties noticeProperties,
                                 ObjectMapper objectMapper, ScheduledFactory scheduledFactory) {
        this.gitHubClient = gitHubClient;
        this.weComWebhookClient = weComWebhookClient;
        this.noticeProperties = noticeProperties;
        this.objectMapper = objectMapper;
        this.scheduledFactory = scheduledFactory;
    }

    @Scheduled(cron = "0 0/1 * * * ?")
    public void pollGitHubAndNotify() {
        LocalDateTime taskStartedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        log.info("Notice scheduler date:{}", taskStartedAt);
        if (!noticeProperties.isEnabled()) {
            log.debug("Notice scheduler is disabled.");
            return;
        }
        if (!gitHubClient.isConfigured() || !weComWebhookClient.isConfigured()) {
            log.warn("Missing required environment variables. Please set GITHUB_TOKEN, GITHUB_API_URL and WECOM_WEBHOOK_URL.");
            return;
        }

        try {
            String payload = gitHubClient.fetch();
            JsonNode root = objectMapper.readTree(payload);
            List<String> contents = collectMatchedData(root, taskStartedAt);
            if (contents.isEmpty()) {
                log.info("No matched data found at task start time {}, skip notification.", taskStartedAt);
                return;
            }
            for (String content : contents) {
                String truncatedContent = truncate(content, noticeProperties.getMaxContentLength());
                weComWebhookClient.sendText(truncatedContent);
            }
            log.info("WeCom text notification sent.");
        } catch (Exception ex) {
            log.error("Failed to poll GitHub or send WeCom notification.", ex);
        }
    }

    private boolean isOuterTaskTimeMatched(String cron, LocalDateTime taskStartedAt) {
        if (!StringUtils.hasText(cron)) {
            return true;
        }
        try {
            ZonedDateTime zdt = taskStartedAt.atZone(ZoneId.systemDefault()); // 指定时区
            Date date = Date.from(zdt.toInstant());
            CronExpression expression = new CronExpression(cron);
            return expression.isSatisfiedBy(date);
        } catch (IllegalArgumentException | ParseException ex) {
            log.warn("Invalid content cron '{}', skip notification.", cron);
        }
        return false;
    }

    private String extractCron(JsonNode node) {
        JsonNode cronNode = node.get(noticeProperties.getCronField());
        if ((cronNode == null || cronNode.isNull()) && !"cron".equals(noticeProperties.getCronField())) {
            cronNode = node.get("cron");
        }
        if ((cronNode == null || cronNode.isNull()) && !"corn".equals(noticeProperties.getCronField())) {
            cronNode = node.get("corn");
        }
        return cronNode == null || cronNode.isNull() ? "" : cronNode.asText();
    }

    private List<String> collectMatchedData(JsonNode root, LocalDateTime taskStartedAt) throws Exception {
        JsonNode arrayNode = root;
        if (root.isTextual()) {
            arrayNode = objectMapper.readTree(root.asText());
        }
        if (!arrayNode.isArray()) {
            log.warn("Fetched content must be a JSON array string or JSON array.");
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (item == null || item.isNull() || !item.isObject()) {
                continue;
            }

            String cron = extractCron(item);
            if (!isOuterTaskTimeMatched(cron, taskStartedAt)) {
                continue;
            }

            JsonNode exeNode = item.get("exeCode");
            if (exeNode != null) {
                String exeCode = exeNode.asText();
                ZonedDateTime zdt = taskStartedAt.atZone(ZoneId.systemDefault()); // 指定时区
                Date date = Date.from(zdt.toInstant());
                scheduledFactory.getScheduledService(exeCode).execute(date);
            } else {
                JsonNode dataNode = item.get(noticeProperties.getDataField());
                if (dataNode == null || dataNode.isNull()) {
                    continue;
                }
                if (dataNode.isTextual()) {
                    lines.add(dataNode.asText());
                } else {
                    lines.add(objectMapper.writeValueAsString(dataNode));
                }
            }
        }
        return lines;
    }

    private String truncate(String value, int maxLength) {
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...content truncated";
    }

}
