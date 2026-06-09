package com.jkoi.notice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jkoi.notice.client.GitHubClient;
import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.config.NoticeProperties;
import com.jkoi.notice.model.ReminderConfig;
import com.jkoi.notice.model.ReminderField;
import com.jkoi.notice.model.ReminderStatRecord;
import com.jkoi.notice.model.ReminderStats;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReminderConfigService {

    private final ObjectMapper objectMapper;
    private final WeComWebhookClient weComWebhookClient;
    private final GitHubClient gitHubClient;
    private final ScheduledFactory scheduledFactory;
    private final NoticeProperties noticeProperties;
    private final Path storagePath;
    private final Path statsStoragePath;
    private final String githubStatsFilePath;
    private List<ReminderConfig> reminders = new ArrayList<ReminderConfig>();
    private ReminderStats stats = new ReminderStats();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ReminderConfigService(ObjectMapper objectMapper,
                                 WeComWebhookClient weComWebhookClient,
                                 GitHubClient gitHubClient,
                                 ScheduledFactory scheduledFactory,
                                 NoticeProperties noticeProperties,
                                 @Value("${notice-web.storage-path:data/reminders.json}") String storagePath,
                                 @Value("${notice-web.stats-storage-path:data/reminder-stats.json}") String statsStoragePath,
                                 @Value("${github.stats-file-path:/notice/notice-stats.json}") String githubStatsFilePath) {
        this.objectMapper = objectMapper;
        this.weComWebhookClient = weComWebhookClient;
        this.gitHubClient = gitHubClient;
        this.scheduledFactory = scheduledFactory;
        this.noticeProperties = noticeProperties;
        this.storagePath = Paths.get(storagePath);
        this.statsStoragePath = Paths.get(statsStoragePath);
        this.githubStatsFilePath = githubStatsFilePath;
    }

    @PostConstruct
    public synchronized void init() {
        List<ReminderConfig> loaded = readLocalReminders();
        List<ReminderConfig> normalized = new ArrayList<ReminderConfig>();
        for (ReminderConfig reminder : loaded) {
            normalized.add(normalize(reminder));
        }
        this.reminders = normalized;
        this.stats = ensureTodayStats(readStats());
    }

    public synchronized Map<String, Object> getDashboard() {
        refreshFromSource();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", list());
        result.put("stats", buildStats());
        result.put("exportJson", exportSchedulerPayloadFrom(reminders));
        result.put("source", gitHubClient.isConfigured() ? "github" : "local");
        return result;
    }

    public synchronized List<ReminderConfig> list() {
        List<ReminderConfig> result = new ArrayList<ReminderConfig>();
        for (ReminderConfig reminder : reminders) {
            if (!reminder.isDeleted()) {
                result.add(reminder);
            }
        }
        return result;
    }

    public synchronized ReminderConfig save(ReminderConfig input) {
        List<ReminderConfig> current = readSourceReminders();
        ReminderConfig normalized = normalize(input);
        boolean updated = false;
        for (int i = 0; i < current.size(); i++) {
            if (normalized.getId().equals(current.get(i).getId())) {
                if (current.get(i).isDeleted()) {
                    normalized.setDeleted(true);
                    normalized.setEnabled(false);
                }
                current.set(i, normalized);
                updated = true;
                break;
            }
        }
        if (!updated) {
            current.add(0, normalized);
        }
        writeSourceReminders(current);
        reminders = current;
        return normalized;
    }

    public synchronized boolean delete(String id) {
        if (!StringUtils.hasText(id)) {
            return false;
        }
        List<ReminderConfig> current = readSourceReminders();
        boolean deleted = false;
        for (ReminderConfig reminder : current) {
            if (id.equals(reminder.getId())) {
                reminder.setDeleted(true);
                reminder.setEnabled(false);
                deleted = true;
                break;
            }
        }
        if (deleted) {
            writeSourceReminders(current);
            reminders = current;
        }
        return deleted;
    }

    public synchronized ArrayNode exportSchedulerPayload() {
        refreshFromSource();
        return exportSchedulerPayloadFrom(reminders);
    }

    private ArrayNode exportSchedulerPayloadFrom(List<ReminderConfig> source) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (ReminderConfig reminder : source) {
            if (!reminder.isDeleted() && reminder.isEnabled()) {
                arrayNode.add(toSchedulerPayload(reminder));
            }
        }
        return arrayNode;
    }

    public synchronized Map<String, Object> buildStats() {
        return toStatsMap(syncStatsWithEnabled(true));
    }

    public synchronized void recordScheduleResult(int matchedCount, int errorCount, String errorMessage) {
        List<ReminderStatRecord> matchedRecords = new ArrayList<ReminderStatRecord>();
        for (int i = 0; i < matchedCount; i++) {
            matchedRecords.add(new ReminderStatRecord("", "未命名提醒", "", "", "", ""));
        }
        List<ReminderStatRecord> errorRecords = new ArrayList<ReminderStatRecord>();
        for (int i = 0; i < errorCount; i++) {
            errorRecords.add(new ReminderStatRecord("", "未命名提醒", "", "", "", errorMessage));
        }
        recordScheduleResult(matchedRecords, errorRecords, errorMessage);
    }

    public synchronized void recordScheduleResult(List<ReminderStatRecord> matchedRecords,
                                                  List<ReminderStatRecord> errorRecords,
                                                  String errorMessage) {
        int matchedCount = matchedRecords == null ? 0 : matchedRecords.size();
        int errorCount = errorRecords == null ? 0 : errorRecords.size();
        if (matchedCount <= 0 && errorCount <= 0) {
            return;
        }

        try {
            reminders = readSourceReminders();
        } catch (Exception ignored) {
            // Keep the latest in-memory reminders so stats can still be updated.
        }
        ReminderStats current = ensureTodayStats(readStats());
        current.setEnabled(countEnabled(reminders));
        String now = LocalDateTime.now().withNano(0).toString();
        if (matchedCount > 0) {
            current.setTodayMatched(current.getTodayMatched() + matchedCount);
            current.setLastMatchedAt(now);
            current.setTodayRecords(appendRecords(current.getTodayRecords(), matchedRecords, now, ""));
        }
        if (errorCount > 0) {
            current.setErrors(current.getErrors() + errorCount);
            current.setLastErrorAt(now);
            current.setLastErrorMessage(truncate(errorMessage, 500));
            current.setErrorRecords(appendRecords(current.getErrorRecords(), errorRecords, now, errorMessage));
        }
        writeStats(current);
        stats = current;
    }

    public Map<String, Object> testSend(ReminderConfig input) {
        ReminderConfig reminder = normalize(input);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        ObjectNode payload = toSchedulerPayload(reminder);
        Map<String, Object> match = matchCronAtTestTime(reminder.getCron(), reminder.getTestDate());
        result.put("testDate", match.get("testDate"));
        result.put("matched", match.get("matched"));
        result.put("matchedAt", match.get("matchedAt"));
        if (!Boolean.TRUE.equals(match.get("matched"))) {
            result.put("sent", false);
            result.put("message", match.get("message"));
            result.put("content", buildTestContent(payload));
            return result;
        }
        if (!weComWebhookClient.isConfigured()) {
            result.put("sent", false);
            result.put("message", "未配置企微机器人地址，无法测试发送");
            result.put("content", buildTestContent(payload));
            return result;
        }
        Date matchedAt = (Date) match.get("matchedDate");
        String content = executeTestPayload(payload, matchedAt);
        result.put("sent", true);
        result.put("message", "测试提醒已发送");
        result.put("content", content);
        return result;
    }

    public Map<String, Object> previewCron(ReminderConfig input) {
        ReminderConfig reminder = input == null ? new ReminderConfig() : input;
        return previewCron(reminder.getCron());
    }

    private Map<String, Object> previewCron(String cron) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("valid", false);
        result.put("nextTimes", new ArrayList<String>());
        if (!StringUtils.hasText(cron)) {
            result.put("message", "请先填写 Cron 表达式");
            return result;
        }
        try {
            CronExpression expression = new CronExpression(cron);
            result.put("valid", true);
            result.put("nextTimes", nextFireTimes(expression, new Date(), 7));
            result.put("message", "Cron 表达式有效");
        } catch (Exception ex) {
            result.put("message", "Cron 表达式无效");
        }
        return result;
    }

    private Map<String, Object> matchCronAtTestTime(String cron, String testDate) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("matched", false);
        result.put("matchedAt", "");
        if (!StringUtils.hasText(cron)) {
            result.put("testDate", defaultTestDateTime(testDate));
            result.put("message", "请先填写 Cron 表达式");
            return result;
        }
        try {
            CronExpression expression = new CronExpression(cron);
            LocalDateTime dateTime = parseTestDateTime(testDate);
            Date matchedAt = toDate(dateTime);
            result.put("testDate", formatDateTime(matchedAt));
            if (expression.isSatisfiedBy(matchedAt)) {
                result.put("matched", true);
                result.put("matchedAt", formatDateTime(matchedAt));
                result.put("matchedDate", matchedAt);
                result.put("message", "测试时间命中 Cron 表达式");
                return result;
            }
            result.put("message", "测试时间未命中 Cron 表达式，不发送");
        } catch (Exception ex) {
            result.put("testDate", defaultTestDateTime(testDate));
            result.put("message", "Cron 表达式或测试时间无效");
        }
        return result;
    }

    private List<String> nextFireTimes(CronExpression expression, Date start, int limit) {
        List<String> result = new ArrayList<String>();
        Date cursor = start;
        for (int i = 0; i < limit; i++) {
            Date next = expression.getNextValidTimeAfter(cursor);
            if (next == null) {
                break;
            }
            result.add(formatDateTime(next));
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(next);
            calendar.add(Calendar.SECOND, 1);
            cursor = calendar.getTime();
        }
        return result;
    }

    private LocalDateTime parseTestDateTime(String testDate) {
        if (!StringUtils.hasText(testDate)) {
            return LocalDateTime.now().withSecond(0).withNano(0);
        }
        String value = testDate.trim();
        if (value.length() == 10) {
            return LocalDate.parse(value).atStartOfDay();
        }
        if (value.length() == 16) {
            return LocalDateTime.parse(value + ":00", DATE_TIME_FORMATTER);
        }
        if (value.length() > 19) {
            value = value.substring(0, 19);
        }
        return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
    }

    private String defaultTestDateTime(String testDate) {
        try {
            return DATE_TIME_FORMATTER.format(parseTestDateTime(testDate));
        } catch (DateTimeParseException ex) {
            return DATE_TIME_FORMATTER.format(LocalDateTime.now().withSecond(0).withNano(0));
        }
    }

    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private String formatDateTime(Date date) {
        return DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    private String executeTestPayload(ObjectNode payload, Date matchedAt) {
        JsonNode exeNode = payload.get("exeCode");
        if (exeNode != null && StringUtils.hasText(exeNode.asText())) {
            ScheduledService scheduledService = scheduledFactory.getScheduledService(exeNode.asText());
            if (scheduledService == null) {
                throw new IllegalStateException("Unknown execution code: " + exeNode.asText());
            }
            scheduledService.execute(matchedAt, payload);
            return "工作流已执行: " + exeNode.asText();
        }
        String content = buildTestContent(payload);
        if (StringUtils.hasText(content)) {
            weComWebhookClient.sendText(content);
        }
        return content;
    }

    private String buildTestContent(ObjectNode payload) {
        String dataField = resolvePayloadDataField(payload);
        JsonNode dataNode = payload.get(dataField);
        if ((dataNode == null || dataNode.isNull()) && !"data".equals(dataField)) {
            dataNode = payload.get("data");
        }
        if (dataNode == null || dataNode.isNull()) {
            return "";
        }
        if (dataNode.isTextual()) {
            return dataNode.asText();
        }
        try {
            return objectMapper.writeValueAsString(dataNode);
        } catch (Exception ex) {
            return dataNode.toString();
        }
    }

    private String resolvePayloadDataField(ObjectNode payload) {
        JsonNode dataFieldNode = payload.get("dataField");
        if (dataFieldNode != null && dataFieldNode.isTextual() && StringUtils.hasText(dataFieldNode.asText())) {
            return dataFieldNode.asText();
        }
        String configuredDataField = StringUtils.hasText(noticeProperties.getDataField()) ? noticeProperties.getDataField() : "data";
        if (payload.has(configuredDataField)) {
            return configuredDataField;
        }
        if (payload.has("data")) {
            return "data";
        }
        Iterator<String> fieldNames = payload.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!isMetadataField(fieldName)) {
                return fieldName;
            }
        }
        return configuredDataField;
    }

    private ReminderConfig normalize(ReminderConfig input) {
        ReminderConfig reminder = input == null ? new ReminderConfig() : input;
        if (!StringUtils.hasText(reminder.getId())) {
            reminder.setId("r-" + UUID.randomUUID().toString().replace("-", ""));
        }
        reminder.setTitle(defaultText(reminder.getTitle(), "未命名提醒"));
        reminder.setType(normalizeType(reminder.getType()));
        reminder.setCron(trim(reminder.getCron()));
        reminder.setData(trim(reminder.getData()));
        reminder.setExeCode(trim(reminder.getExeCode()));
        reminder.setDataField(defaultText(reminder.getDataField(), "data"));
        reminder.setFields(normalizeFields(reminder));
        syncLegacyData(reminder);
        return reminder;
    }

    private List<ReminderField> normalizeFields(ReminderConfig reminder) {
        List<ReminderField> result = new ArrayList<ReminderField>();
        if (reminder.getFields() != null) {
            for (ReminderField field : reminder.getFields()) {
                if (field == null) {
                    continue;
                }
                String name = trim(field.getName());
                if (!StringUtils.hasText(name) || isMetadataField(name)) {
                    continue;
                }
                result.add(new ReminderField(name, trim(field.getValue())));
            }
        }
        if (result.isEmpty() && StringUtils.hasText(reminder.getData())) {
            result.add(new ReminderField(defaultText(reminder.getDataField(), "data"), reminder.getData()));
        }
        if (result.isEmpty()) {
            result.add(new ReminderField("data", ""));
        }
        return result;
    }

    private void syncLegacyData(ReminderConfig reminder) {
        if (reminder.getFields() == null || reminder.getFields().isEmpty()) {
            reminder.setDataField(defaultText(reminder.getDataField(), "data"));
            reminder.setData(trim(reminder.getData()));
            return;
        }
        ReminderField first = reminder.getFields().get(0);
        reminder.setDataField(defaultText(first.getName(), "data"));
        reminder.setData(trim(first.getValue()));
    }

    private void refreshFromSource() {
        reminders = readSourceReminders();
    }

    private List<ReminderConfig> readSourceReminders() {
        if (gitHubClient.isConfigured()) {
            return readGitHubReminders();
        }
        return readLocalReminders();
    }

    private void writeSourceReminders(List<ReminderConfig> source) {
        if (gitHubClient.isConfigured()) {
            writeGitHubReminders(source);
            return;
        }
        writeLocalReminders(source);
    }

    private ReminderStats syncStatsWithEnabled(boolean persist) {
        ReminderStats current = readStats();
        boolean changed = ensureTodayStatsChanged(current);
        int enabled = countEnabled(reminders);
        if (current.getEnabled() != enabled) {
            current.setEnabled(enabled);
            changed = true;
        }
        if (current.getTodayRecords() == null) {
            current.setTodayRecords(new ArrayList<ReminderStatRecord>());
            changed = true;
        }
        if (current.getErrorRecords() == null) {
            current.setErrorRecords(new ArrayList<ReminderStatRecord>());
            changed = true;
        }
        if (changed && persist) {
            writeStatsQuietly(current);
        }
        stats = current;
        return current;
    }

    private int countEnabled(List<ReminderConfig> source) {
        int enabled = 0;
        for (ReminderConfig reminder : source) {
            if (!reminder.isDeleted() && reminder.isEnabled()) {
                enabled++;
            }
        }
        return enabled;
    }

    private ReminderStats readStats() {
        try {
            if (gitHubClient.isConfigured()) {
                return readGitHubStats();
            }
            return readLocalStats();
        } catch (Exception ignored) {
            return new ReminderStats();
        }
    }

    private ReminderStats readGitHubStats() {
        GitHubClient.GitHubFileContent content = gitHubClient.fetchContent(resolveGitHubStatsFilePath());
        return content == null ? new ReminderStats() : parseStats(content.getContent());
    }

    private ReminderStats readLocalStats() {
        if (!Files.exists(statsStoragePath)) {
            return new ReminderStats();
        }
        try {
            String json = new String(Files.readAllBytes(statsStoragePath), StandardCharsets.UTF_8);
            return parseStats(json);
        } catch (Exception ex) {
            return new ReminderStats();
        }
    }

    private ReminderStats parseStats(String json) {
        if (!StringUtils.hasText(json)) {
            return new ReminderStats();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isTextual()) {
                root = objectMapper.readTree(root.asText());
            }
            if (!root.isObject()) {
                return new ReminderStats();
            }
            return objectMapper.treeToValue(root, ReminderStats.class);
        } catch (Exception ex) {
            return new ReminderStats();
        }
    }

    private void writeStats(ReminderStats current) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(current);
            if (gitHubClient.isConfigured()) {
                gitHubClient.save(resolveGitHubStatsFilePath(), json);
                return;
            }
            Path parent = statsStoragePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(statsStoragePath, json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write reminder stats.", ex);
        }
    }

    private void writeStatsQuietly(ReminderStats current) {
        try {
            writeStats(current);
        } catch (Exception ignored) {
        }
    }

    private ReminderStats ensureTodayStats(ReminderStats current) {
        ReminderStats result = current == null ? new ReminderStats() : current;
        ensureTodayStatsChanged(result);
        return result;
    }

    private boolean ensureTodayStatsChanged(ReminderStats current) {
        if (current == null) {
            return false;
        }
        String today = LocalDate.now().toString();
        if (today.equals(current.getDate())) {
            return false;
        }
        current.setDate(today);
        current.setTodayMatched(0);
        current.setErrors(0);
        current.setLastMatchedAt("");
        current.setLastErrorAt("");
        current.setLastErrorMessage("");
        current.setTodayRecords(new ArrayList<ReminderStatRecord>());
        current.setErrorRecords(new ArrayList<ReminderStatRecord>());
        return true;
    }

    private Map<String, Object> toStatsMap(ReminderStats current) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("date", current.getDate());
        result.put("enabled", current.getEnabled());
        result.put("todayMatched", current.getTodayMatched());
        result.put("errors", current.getErrors());
        result.put("lastMatchedAt", current.getLastMatchedAt());
        result.put("lastErrorAt", current.getLastErrorAt());
        result.put("lastErrorMessage", current.getLastErrorMessage());
        result.put("todayRecords", current.getTodayRecords() == null ? new ArrayList<ReminderStatRecord>() : current.getTodayRecords());
        result.put("errorRecords", current.getErrorRecords() == null ? new ArrayList<ReminderStatRecord>() : current.getErrorRecords());
        return result;
    }

    private String resolveGitHubStatsFilePath() {
        return defaultText(githubStatsFilePath, "/notice/notice-stats.json");
    }

    private ObjectNode toSchedulerPayload(ReminderConfig reminder) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", reminder.getId());
        payload.put("title", reminder.getTitle());
        payload.put("type", reminder.getType());
        if (StringUtils.hasText(reminder.getCron())) {
            payload.put("cron", reminder.getCron());
        }

        if (isFixedFlow(reminder.getType())) {
            payload.put("exeCode", reminder.getExeCode());
        }
        putFields(payload, reminder);
        return payload;
    }

    private boolean isFixedFlow(String type) {
        return "flow".equals(type) || "task".equals(type);
    }

    private String normalizeType(String type) {
        String normalized = defaultText(type, "text");
        if ("flow".equals(normalized) || "task".equals(normalized)) {
            return "flow";
        }
        return "text";
    }

    private void putData(ObjectNode payload, String dataField, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            JsonNode data = objectMapper.readTree(value);
            payload.set(dataField, data);
        } catch (Exception ex) {
            payload.put(dataField, value);
        }
    }

    private void putFields(ObjectNode payload, ReminderConfig reminder) {
        if (reminder.getFields() == null) {
            return;
        }
        for (ReminderField field : reminder.getFields()) {
            if (field != null && StringUtils.hasText(field.getName())) {
                putData(payload, field.getName(), field.getValue());
            }
        }
    }

    private ObjectNode toConfigPayload(ReminderConfig reminder) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", reminder.getId());
        payload.put("title", reminder.getTitle());
        payload.put("type", reminder.getType());
        payload.put("enabled", reminder.isEnabled());
        if (reminder.isDeleted()) {
            payload.put("deleted", true);
        }
        if (StringUtils.hasText(reminder.getCron())) {
            payload.put("cron", reminder.getCron());
        }

        if (isFixedFlow(reminder.getType())) {
            payload.put("exeCode", reminder.getExeCode());
        }
        putFields(payload, reminder);
        return payload;
    }

    private List<ReminderConfig> readLocalReminders() {
        if (!Files.exists(storagePath)) {
            return new ArrayList<ReminderConfig>();
        }
        try {
            String json = new String(Files.readAllBytes(storagePath), StandardCharsets.UTF_8);
            if (!StringUtils.hasText(json)) {
                return new ArrayList<ReminderConfig>();
            }
            return parseReminders(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read reminder config: " + storagePath, ex);
        }
    }

    private List<ReminderConfig> readGitHubReminders() {
        return parseReminders(gitHubClient.fetchContent().getContent());
    }

    private List<ReminderConfig> parseReminders(String json) {
        List<ReminderConfig> result = new ArrayList<ReminderConfig>();
        if (!StringUtils.hasText(json)) {
            return result;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isTextual()) {
                root = objectMapper.readTree(root.asText());
            }
            if (root.has("items") && root.get("items").isArray()) {
                root = root.get("items");
            }
            if (!root.isArray()) {
                return result;
            }
            for (int i = 0; i < root.size(); i++) {
                JsonNode item = root.get(i);
                if (item != null && item.isObject()) {
                    result.add(normalize(fromConfigNode(item, i)));
                }
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse reminder config from GitHub.", ex);
        }
    }

    private ReminderConfig fromConfigNode(JsonNode item, int index) throws Exception {
        ReminderConfig reminder = new ReminderConfig();
        String id = textOf(item, "id");
        reminder.setId(StringUtils.hasText(id) ? id : buildSyntheticId(item));

        String type = textOf(item, "type");
        if (!StringUtils.hasText(type)) {
            type = item.has("exeCode") ? "flow" : "text";
        }
        reminder.setType(type);
        reminder.setCron(firstText(item, noticeProperties.getCronField(), "cron", "corn"));
        reminder.setExeCode(textOf(item, "exeCode"));
        reminder.setDeleted(item.has("deleted") && item.get("deleted").asBoolean(false));
        reminder.setEnabled(!item.has("enabled") || item.get("enabled").asBoolean(true));
        reminder.setFields(readFields(item));

        String title = textOf(item, "title");
        reminder.setTitle(StringUtils.hasText(title) ? title : buildTitle(reminder, index));
        return reminder;
    }

    private void writeGitHubReminders(List<ReminderConfig> source) {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (ReminderConfig reminder : source) {
                arrayNode.add(toConfigPayload(normalize(reminder)));
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(arrayNode);
            gitHubClient.save(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write reminder config to GitHub.", ex);
        }
    }

    private void writeLocalReminders(List<ReminderConfig> source) {
        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (ReminderConfig reminder : source) {
                arrayNode.add(toConfigPayload(normalize(reminder)));
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(arrayNode);
            Files.write(storagePath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write reminder config: " + storagePath, ex);
        }
    }

    private List<ReminderField> readFields(JsonNode item) throws Exception {
        List<ReminderField> fields = new ArrayList<ReminderField>();
        JsonNode fieldsNode = item.get("fields");
        if (fieldsNode != null && fieldsNode.isArray()) {
            for (JsonNode fieldNode : fieldsNode) {
                String name = textOf(fieldNode, "name");
                if (StringUtils.hasText(name) && !isMetadataField(name)) {
                    fields.add(new ReminderField(name, jsonValueToText(fieldNode.get("value"))));
                }
            }
        }
        Iterator<String> fieldNames = item.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!isMetadataField(fieldName)) {
                fields.add(new ReminderField(fieldName, jsonValueToText(item.get(fieldName))));
            }
        }
        return fields;
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
                || "testDate".equals(fieldName)
                || "fields".equals(fieldName);
    }

    private String textOf(JsonNode item, String fieldName) {
        if (item == null || !StringUtils.hasText(fieldName)) {
            return "";
        }
        JsonNode value = item.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private String firstText(JsonNode item, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = textOf(item, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String jsonValueToText(JsonNode value) throws Exception {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual()) {
            return value.asText();
        }
        return objectMapper.writeValueAsString(value);
    }

    private String buildSyntheticId(JsonNode item) {
        return "g-" + DigestUtils.md5DigestAsHex(item.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String buildTitle(ReminderConfig reminder, int index) {
        if (isFixedFlow(reminder.getType())) {
            return defaultText(reminder.getExeCode(), "\u5de5\u4f5c\u6d41 " + (index + 1));
        }
        String data = trim(reminder.getData());
        if (!StringUtils.hasText(data) && reminder.getFields() != null && !reminder.getFields().isEmpty()) {
            data = trim(reminder.getFields().get(0).getValue());
        }
        if (StringUtils.hasText(data)) {
            return data.length() > 18 ? data.substring(0, 18) : data;
        }
        return "\u6587\u672c\u63d0\u9192 " + (index + 1);
    }

    private String defaultText(String value, String defaultValue) {
        String trimmed = trim(value);
        return StringUtils.hasText(trimmed) ? trimmed : defaultValue;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int maxLength) {
        String text = trim(value);
        if (maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private List<ReminderStatRecord> appendRecords(List<ReminderStatRecord> current,
                                                   List<ReminderStatRecord> additions,
                                                   String occurredAt,
                                                   String defaultMessage) {
        List<ReminderStatRecord> result = new ArrayList<ReminderStatRecord>();
        if (current != null) {
            result.addAll(current);
        }
        if (additions != null) {
            for (ReminderStatRecord addition : additions) {
                result.add(normalizeStatRecord(addition, occurredAt, defaultMessage));
            }
        }
        int maxRecords = 200;
        if (result.size() > maxRecords) {
            return new ArrayList<ReminderStatRecord>(result.subList(result.size() - maxRecords, result.size()));
        }
        return result;
    }

    private ReminderStatRecord normalizeStatRecord(ReminderStatRecord source,
                                                   String occurredAt,
                                                   String defaultMessage) {
        ReminderStatRecord record = source == null ? new ReminderStatRecord() : source;
        record.setId(trim(record.getId()));
        record.setTitle(defaultText(record.getTitle(), "未命名提醒"));
        record.setType(normalizeType(record.getType()));
        record.setCron(trim(record.getCron()));
        record.setExeCode(trim(record.getExeCode()));
        record.setMessage(defaultText(record.getMessage(), defaultMessage));
        record.setOccurredAt(defaultText(record.getOccurredAt(), occurredAt));
        return record;
    }
}
