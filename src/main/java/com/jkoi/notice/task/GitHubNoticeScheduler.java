package com.jkoi.notice.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkoi.notice.client.GitHubClient;
import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.config.NoticeProperties;
import com.jkoi.notice.model.ReminderStatRecord;
import com.jkoi.notice.service.ReminderConfigService;
import com.jkoi.notice.service.ScheduledFactory;
import com.jkoi.notice.service.ScheduledService;
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
    private final ReminderConfigService reminderConfigService;

    public GitHubNoticeScheduler(GitHubClient gitHubClient,
                                 WeComWebhookClient weComWebhookClient,
                                 NoticeProperties noticeProperties,
                                 ObjectMapper objectMapper,
                                 ScheduledFactory scheduledFactory,
                                 ReminderConfigService reminderConfigService) {
        this.gitHubClient = gitHubClient;
        this.weComWebhookClient = weComWebhookClient;
        this.noticeProperties = noticeProperties;
        this.objectMapper = objectMapper;
        this.scheduledFactory = scheduledFactory;
        this.reminderConfigService = reminderConfigService;
    }

    @Scheduled(cron = "0 0/1 * * * ?")
    public void pollGitHubAndNotify() {
        LocalDateTime taskStartedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        PollResult pollResult = new PollResult();
        log.info("Notice scheduler date:{}", taskStartedAt);
        if (!noticeProperties.isEnabled()) {
            log.debug("Notice scheduler is disabled.");
            return;
        }
        if (!weComWebhookClient.isConfigured()) {
            log.warn("Missing required environment variable. Please set WECOM_WEBHOOK_URL.");
            return;
        }

        try {
            JsonNode root = loadReminderPayload();
            pollResult = collectMatchedData(root, taskStartedAt);
            if (pollResult.getContents().isEmpty()) {
                if (pollResult.getMatchedCount() > 0) {
                    log.info("Matched workflow reminder executed at task start time {}.", taskStartedAt);
                } else {
                    log.info("No matched data found at task start time {}, skip notification.", taskStartedAt);
                }
                return;
            }
            for (ContentEntry entry : pollResult.getContents()) {
                try {
                    String truncatedContent = truncate(entry.getContent(), noticeProperties.getMaxContentLength());
                    weComWebhookClient.sendText(truncatedContent);
                    pollResult.addMatched(entry.getRecord());
                } catch (Exception ex) {
                    pollResult.addError(entry.getRecord(), "Send failed: " + shortMessage(ex));
                }
            }
            log.info("WeCom text notification sent.");
        } catch (Exception ex) {
            pollResult.addError(null, shortMessage(ex));
            log.error("Failed to poll GitHub or send WeCom notification.", ex);
        } finally {
            recordStats(pollResult);
        }
    }

    private JsonNode loadReminderPayload() throws Exception {
        return reminderConfigService.exportSchedulerPayload();
    }

    private Boolean isOuterTaskTimeMatched(String cron, LocalDateTime taskStartedAt) {
        if (!StringUtils.hasText(cron)) {
            return Boolean.TRUE;
        }
        try {
            ZonedDateTime zdt = taskStartedAt.atZone(ZoneId.systemDefault());
            Date date = Date.from(zdt.toInstant());
            CronExpression expression = new CronExpression(cron);
            return expression.isSatisfiedBy(date);
        } catch (IllegalArgumentException | ParseException ex) {
            log.warn("Invalid content cron '{}', skip notification.", cron);
        }
        return null;
    }

    private String extractCron(JsonNode node) {
        JsonNode cronNode = node.get(noticeProperties.getCronField());
        if ((cronNode == null || cronNode.isNull()) && !"cron".equals(noticeProperties.getCronField())) {
            cronNode = node.get("cron");
        }
        return cronNode == null || cronNode.isNull() ? "" : cronNode.asText();
    }

    private String extractDataField(JsonNode node) {
        JsonNode dataFieldNode = node.get("dataField");
        if (dataFieldNode != null && dataFieldNode.isTextual() && StringUtils.hasText(dataFieldNode.asText())) {
            return dataFieldNode.asText();
        }
        String configuredDataField = StringUtils.hasText(noticeProperties.getDataField()) ? noticeProperties.getDataField() : "data";
        if (node.has(configuredDataField)) {
            return configuredDataField;
        }
        if (node.has("data")) {
            return "data";
        }
        java.util.Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!isMetadataField(fieldName)) {
                return fieldName;
            }
        }
        return configuredDataField;
    }

    private boolean isMetadataField(String fieldName) {
        return "id".equals(fieldName)
                || "title".equals(fieldName)
                || "type".equals(fieldName)
                || "enabled".equals(fieldName)
                || "deleted".equals(fieldName)
                || "cron".equals(fieldName)
                || "corn".equals(fieldName)
                || fieldName.equals(noticeProperties.getCronField())
                || "exeCode".equals(fieldName)
                || "dataField".equals(fieldName)
                || "fields".equals(fieldName)
                || "updatedAt".equals(fieldName);
    }

    private PollResult collectMatchedData(JsonNode root, LocalDateTime taskStartedAt) throws Exception {
        JsonNode arrayNode = root;
        if (root.isTextual()) {
            arrayNode = objectMapper.readTree(root.asText());
        }
        PollResult result = new PollResult();
        if (!arrayNode.isArray()) {
            log.warn("Fetched content must be a JSON array string or JSON array.");
            result.addError(null, "Fetched content must be a JSON array string or JSON array.");
            return result;
        }

        for (int itemIndex = 0; itemIndex < arrayNode.size(); itemIndex++) {
            JsonNode item = arrayNode.get(itemIndex);
            if (item == null || item.isNull() || !item.isObject()) {
                continue;
            }
            ReminderStatRecord record = buildRecord(item);
            JsonNode enabledNode = item.get("enabled");
            if (enabledNode != null && !enabledNode.asBoolean(true)) {
                continue;
            }
            JsonNode deletedNode = item.get("deleted");
            if (deletedNode != null && deletedNode.asBoolean(false)) {
                continue;
            }

            String cron = extractCron(item);
            Boolean matched = isOuterTaskTimeMatched(cron, taskStartedAt);
            if (matched == null) {
                result.addError(record, "Invalid cron: " + cron);
                continue;
            }
            if (!matched) {
                continue;
            }

            JsonNode exeNode = item.get("exeCode");
            if (exeNode != null) {
                String exeCode = exeNode.asText();
                ZonedDateTime zdt = taskStartedAt.atZone(ZoneId.systemDefault());
                Date date = Date.from(zdt.toInstant());
                ScheduledService scheduledService = scheduledFactory.getScheduledService(exeCode);
                if (scheduledService != null) {
                    try {
                        scheduledService.execute(date, item);
                        result.addMatched(record);
                    } catch (Exception ex) {
                        result.addError(record, "Execution failed for " + exeCode + ": " + shortMessage(ex));
                    }
                } else {
                    result.addError(record, "Unknown execution code: " + exeCode);
                }
            } else {
                String dataField = extractDataField(item);
                JsonNode dataNode = item.get(dataField);
                if ((dataNode == null || dataNode.isNull()) && !"data".equals(dataField)) {
                    dataNode = item.get("data");
                }
                if (dataNode == null || dataNode.isNull()) {
                    result.addError(record, "Missing reminder data field: " + dataField);
                    continue;
                }
                if (dataNode.isTextual()) {
                    result.addContent(dataNode.asText(), record);
                } else {
                    result.addContent(objectMapper.writeValueAsString(dataNode), record);
                }
            }
        }
        return result;
    }

    private ReminderStatRecord buildRecord(JsonNode item) {
        return new ReminderStatRecord(
                textOf(item, "id"),
                textOf(item, "title"),
                textOf(item, "type"),
                extractCron(item),
                textOf(item, "exeCode"),
                ""
        );
    }

    private String textOf(JsonNode item, String fieldName) {
        if (item == null || !StringUtils.hasText(fieldName)) {
            return "";
        }
        JsonNode value = item.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private String truncate(String value, int maxLength) {
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...content truncated";
    }

    private void recordStats(PollResult result) {
        if (result == null || !result.hasStatsChange()) {
            return;
        }
        try {
            reminderConfigService.recordScheduleResult(
                    result.getMatchedRecords(),
                    result.getErrorRecords(),
                    result.getErrorMessage()
            );
        } catch (Exception ex) {
            log.warn("Failed to update reminder stats.", ex);
        }
    }

    private String shortMessage(Exception ex) {
        if (ex == null) {
            return "";
        }
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private static class PollResult {

        private final List<ContentEntry> contents = new ArrayList<ContentEntry>();
        private final List<ReminderStatRecord> matchedRecords = new ArrayList<ReminderStatRecord>();
        private final List<ReminderStatRecord> errorRecords = new ArrayList<ReminderStatRecord>();
        private final StringBuilder errorMessage = new StringBuilder();

        private List<ContentEntry> getContents() {
            return Collections.unmodifiableList(contents);
        }

        private int getMatchedCount() {
            return matchedRecords.size();
        }

        private int getErrorCount() {
            return errorRecords.size();
        }

        private List<ReminderStatRecord> getMatchedRecords() {
            return Collections.unmodifiableList(matchedRecords);
        }

        private List<ReminderStatRecord> getErrorRecords() {
            return Collections.unmodifiableList(errorRecords);
        }

        private String getErrorMessage() {
            return errorMessage.toString();
        }

        private void addContent(String content, ReminderStatRecord record) {
            contents.add(new ContentEntry(content, record));
        }

        private void addMatched(ReminderStatRecord record) {
            matchedRecords.add(record);
        }

        private void addError(ReminderStatRecord record, String message) {
            ReminderStatRecord nextRecord = record == null ? new ReminderStatRecord() : record;
            nextRecord.setMessage(message);
            errorRecords.add(nextRecord);
            if (!StringUtils.hasText(message)) {
                return;
            }
            if (errorMessage.length() > 0) {
                errorMessage.append("; ");
            }
            errorMessage.append(message);
        }

        private boolean hasStatsChange() {
            return !matchedRecords.isEmpty() || !errorRecords.isEmpty();
        }
    }

    private static class ContentEntry {

        private final String content;
        private final ReminderStatRecord record;

        private ContentEntry(String content, ReminderStatRecord record) {
            this.content = content;
            this.record = record;
        }

        private String getContent() {
            return content;
        }

        private ReminderStatRecord getRecord() {
            return record;
        }
    }
}
