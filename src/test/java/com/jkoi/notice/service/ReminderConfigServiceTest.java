package com.jkoi.notice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkoi.notice.client.GitHubClient;
import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.config.NoticeProperties;
import com.jkoi.notice.model.ReminderConfig;
import com.jkoi.notice.model.ReminderField;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

class ReminderConfigServiceTest {

//    private static final String STATS_PATH = "/notice/notice-stats.json";
//
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    @Test
//    void dashboardReadsGitHubSchedulerJson() {
//        GitHubClient gitHubClient = mockGitHubClient(
//                "[{\"cron\":\"0 0 8 * * ?\",\"data\":\"hello\"},"
//                        + "{\"exeCode\":\"BIRTHDAY_REMINDER\",\"data\":[{\"name\":\"Alice\"}]}]"
//        );
//        ReminderConfigService service = service(gitHubClient);
//
//        Map<String, Object> dashboard = service.getDashboard();
//        List<ReminderConfig> items = (List<ReminderConfig>) dashboard.get("items");
//
//        assertThat(dashboard).containsEntry("source", "github");
//        assertThat(items).hasSize(2);
//        assertThat(items.get(0).getType()).isEqualTo("text");
//        assertThat(items.get(0).getData()).isEqualTo("hello");
//        assertThat(items.get(0).getFields()).extracting("name").containsExactly("data");
//        assertThat(items.get(1).getType()).isEqualTo("flow");
//        assertThat(items.get(1).getExeCode()).isEqualTo("BIRTHDAY_REMINDER");
//        assertThat(items.get(1).getData()).contains("Alice");
//    }
//
//    @Test
//    void dashboardMatchesArbitraryJsonFields() {
//        GitHubClient gitHubClient = mockGitHubClient(
//                "[{\"id\":\"r-json\",\"title\":\"custom\",\"cron\":\"0 0 8 * * ?\","
//                        + "\"message\":\"hello\",\"details\":{\"level\":2}}]"
//        );
//        ReminderConfigService service = service(gitHubClient);
//
//        Map<String, Object> dashboard = service.getDashboard();
//        List<ReminderConfig> items = (List<ReminderConfig>) dashboard.get("items");
//
//        assertThat(items).hasSize(1);
//        assertThat(items.get(0).getFields()).extracting("name").containsExactly("message", "details");
//        assertThat(items.get(0).getFields().get(1).getValue()).contains("\"level\":2");
//    }
//
//    @Test
//    void saveWritesGitHubConfigJson() throws Exception {
//        GitHubClient gitHubClient = mockGitHubClient("[]");
//        ReminderConfigService service = service(gitHubClient);
//        ReminderConfig reminder = new ReminderConfig();
//        reminder.setId("r-1");
//        reminder.setTitle("GitHub reminder");
//        reminder.setType("text");
//        reminder.setCron("0 0 9 * * ?");
//        reminder.setFields(java.util.Collections.singletonList(new ReminderField("message", "from page")));
//        reminder.setEnabled(true);
//
//        service.save(reminder);
//
//        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
//        verify(gitHubClient).save(captor.capture());
//        JsonNode root = objectMapper.readTree(captor.getValue());
//        assertThat(root).hasSize(1);
//        assertThat(root.get(0).get("id").asText()).isEqualTo("r-1");
//        assertThat(root.get(0).get("title").asText()).isEqualTo("GitHub reminder");
//        assertThat(root.get(0).get("message").asText()).isEqualTo("from page");
//    }
//
//    @Test
//    void deleteWritesDeletedFlagInsteadOfRemovingItem() throws Exception {
//        GitHubClient gitHubClient = mockGitHubClient(
//                "[{\"id\":\"r-delete\",\"title\":\"delete me\",\"data\":\"keep source\"}]"
//        );
//        ReminderConfigService service = service(gitHubClient);
//
//        boolean deleted = service.delete("r-delete");
//
//        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
//        verify(gitHubClient).save(captor.capture());
//        JsonNode root = objectMapper.readTree(captor.getValue());
//        assertThat(deleted).isTrue();
//        assertThat(root).hasSize(1);
//        assertThat(root.get(0).get("id").asText()).isEqualTo("r-delete");
//        assertThat(root.get(0).get("deleted").asBoolean()).isTrue();
//        assertThat(root.get(0).get("enabled").asBoolean()).isFalse();
//    }
//
//    @Test
//    void saveDoesNotReviveLogicallyDeletedItem() throws Exception {
//        GitHubClient gitHubClient = mockGitHubClient(
//                "[{\"id\":\"r-delete\",\"title\":\"delete me\",\"data\":\"keep source\",\"deleted\":true,\"enabled\":false}]"
//        );
//        ReminderConfigService service = service(gitHubClient);
//        ReminderConfig reminder = new ReminderConfig();
//        reminder.setId("r-delete");
//        reminder.setTitle("stale edit");
//        reminder.setType("text");
//        reminder.setData("old page save");
//        reminder.setEnabled(true);
//
//        service.save(reminder);
//
//        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
//        verify(gitHubClient).save(captor.capture());
//        JsonNode root = objectMapper.readTree(captor.getValue());
//        assertThat(root).hasSize(1);
//        assertThat(root.get(0).get("deleted").asBoolean()).isTrue();
//        assertThat(root.get(0).get("enabled").asBoolean()).isFalse();
//    }
//
//    @Test
//    void dashboardReadsGitHubStatsAndSyncsEnabledCount() throws Exception {
//        String today = java.time.LocalDate.now().toString();
//        GitHubClient gitHubClient = mockGitHubClient(
//                "[{\"id\":\"r-1\",\"data\":\"one\",\"enabled\":true},"
//                        + "{\"id\":\"r-2\",\"data\":\"two\",\"enabled\":false}]"
//        );
//        when(gitHubClient.fetchContent(STATS_PATH)).thenReturn(new GitHubClient.GitHubFileContent(
//                "{\"date\":\"" + today + "\",\"enabled\":0,\"todayMatched\":5,\"errors\":2}",
//                "stats-sha"
//        ));
//        ReminderConfigService service = service(gitHubClient);
//
//        Map<String, Object> dashboard = service.getDashboard();
//        Map<String, Object> stats = (Map<String, Object>) dashboard.get("stats");
//
//        assertThat(stats).containsEntry("enabled", 1);
//        assertThat(stats).containsEntry("todayMatched", 5);
//        assertThat(stats).containsEntry("errors", 2);
//
//        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
//        verify(gitHubClient).save(eq(STATS_PATH), captor.capture());
//        JsonNode savedStats = objectMapper.readTree(captor.getValue());
//        assertThat(savedStats.get("enabled").asInt()).isEqualTo(1);
//        assertThat(savedStats.get("todayMatched").asInt()).isEqualTo(5);
//        assertThat(savedStats.get("errors").asInt()).isEqualTo(2);
//    }
//
//    @Test
//    void recordScheduleResultAccumulatesTodayStatsInGitHub() throws Exception {
//        String today = java.time.LocalDate.now().toString();
//        GitHubClient gitHubClient = mockGitHubClient(
//                "[{\"id\":\"r-1\",\"data\":\"one\",\"enabled\":true},"
//                        + "{\"id\":\"r-2\",\"exeCode\":\"TODAY_REMINDER\",\"enabled\":true}]"
//        );
//        when(gitHubClient.fetchContent(STATS_PATH)).thenReturn(new GitHubClient.GitHubFileContent(
//                "{\"date\":\"" + today + "\",\"enabled\":2,\"todayMatched\":1,\"errors\":0}",
//                "stats-sha"
//        ));
//        ReminderConfigService service = service(gitHubClient);
//
//        service.recordScheduleResult(2, 1, "workflow failed");
//
//        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
//        verify(gitHubClient).save(eq(STATS_PATH), captor.capture());
//        JsonNode savedStats = objectMapper.readTree(captor.getValue());
//        assertThat(savedStats.get("enabled").asInt()).isEqualTo(2);
//        assertThat(savedStats.get("todayMatched").asInt()).isEqualTo(3);
//        assertThat(savedStats.get("errors").asInt()).isEqualTo(1);
//        assertThat(savedStats.get("lastMatchedAt").asText()).isNotBlank();
//        assertThat(savedStats.get("lastErrorMessage").asText()).isEqualTo("workflow failed");
//    }
//
//    @Test
//    void testSendReportsMissingWebhook() {
//        ReminderConfigService service = service(mockGitHubClient("[]"));
//        ReminderConfig reminder = new ReminderConfig();
//        reminder.setTitle("测试消息");
//        reminder.setCron("0 0 0 * * ?");
//        reminder.setTestDate(java.time.LocalDate.now().toString());
//        reminder.setData("hello");
//
//        Map<String, Object> result = service.testSend(reminder);
//
//        assertThat(result).containsEntry("sent", false);
//        assertThat(result.get("message")).asString().contains("未配置企微机器人地址");
//        assertThat(result.get("content")).isEqualTo("hello");
//    }
//
//    @Test
//    void testSendSendsCurrentReminderContent() {
//        GitHubClient gitHubClient = mockGitHubClient("[]");
//        WeComWebhookClient weComWebhookClient = mock(WeComWebhookClient.class);
//        when(weComWebhookClient.isConfigured()).thenReturn(true);
//        ReminderConfigService service = service(gitHubClient, weComWebhookClient);
//        ReminderConfig reminder = new ReminderConfig();
//        reminder.setTitle("接口测试发送");
//        reminder.setType("text");
//        reminder.setCron("0 0 0 * * ?");
//        reminder.setTestDate(java.time.LocalDate.now().toString());
//        reminder.setData("发送内容");
//
//        Map<String, Object> result = service.testSend(reminder);
//
//        assertThat(result).containsEntry("sent", true);
//        assertThat(result.get("content")).isEqualTo("发送内容");
//        verify(weComWebhookClient).sendText(eq("发送内容"));
//    }
//
//    @Test
//    void testSendSkipsWhenTestDateDoesNotMatchCron() {
//        GitHubClient gitHubClient = mockGitHubClient("[]");
//        WeComWebhookClient weComWebhookClient = mock(WeComWebhookClient.class);
//        when(weComWebhookClient.isConfigured()).thenReturn(true);
//        ReminderConfigService service = service(gitHubClient, weComWebhookClient);
//        ReminderConfig reminder = new ReminderConfig();
//        reminder.setTitle("不会发送");
//        reminder.setType("text");
//        reminder.setCron("0 0 9 1 1 ?");
//        reminder.setTestDate("2026-06-09");
//        reminder.setData("发送内容");
//
//        Map<String, Object> result = service.testSend(reminder);
//
//        assertThat(result).containsEntry("sent", false);
//        assertThat(result).containsEntry("matched", false);
//        assertThat(result.get("message")).asString().contains("未命中");
//        verify(weComWebhookClient, never()).sendText(eq("发送内容"));
//    }
//
//    @Test
//    void testSendExecutesWorkflowThroughScheduledService() {
//        GitHubClient gitHubClient = mockGitHubClient("[]");
//        WeComWebhookClient weComWebhookClient = mock(WeComWebhookClient.class);
//        ScheduledFactory scheduledFactory = mock(ScheduledFactory.class);
//        ScheduledService scheduledService = mock(ScheduledService.class);
//        when(weComWebhookClient.isConfigured()).thenReturn(true);
//        when(scheduledFactory.getScheduledService("TODAY_REMINDER")).thenReturn(scheduledService);
//        ReminderConfigService service = service(gitHubClient, weComWebhookClient, scheduledFactory);
//        ReminderConfig reminder = new ReminderConfig();
//        reminder.setTitle("工作流测试");
//        reminder.setType("flow");
//        reminder.setCron("0 30 8 * * ?");
//        reminder.setTestDate("2026-06-09 08:30");
//        reminder.setExeCode("TODAY_REMINDER");
//
//        Map<String, Object> result = service.testSend(reminder);
//
//        assertThat(result).containsEntry("sent", true);
//        assertThat(result).containsEntry("matched", true);
//        verify(scheduledService).execute(any(java.util.Date.class), any(JsonNode.class));
//    }
//
//    private ReminderConfigService service(GitHubClient gitHubClient) {
//        return service(gitHubClient, mock(WeComWebhookClient.class));
//    }
//
//    private ReminderConfigService service(GitHubClient gitHubClient, WeComWebhookClient weComWebhookClient) {
//        return service(gitHubClient, weComWebhookClient, mock(ScheduledFactory.class));
//    }
//
//    private ReminderConfigService service(GitHubClient gitHubClient,
//                                          WeComWebhookClient weComWebhookClient,
//                                          ScheduledFactory scheduledFactory) {
//        NoticeProperties noticeProperties = new NoticeProperties();
//        noticeProperties.setCronField("corn");
//        noticeProperties.setDataField("data");
//        return new ReminderConfigService(
//                objectMapper,
//                weComWebhookClient,
//                gitHubClient,
//                scheduledFactory,
//                noticeProperties,
//                "target/test-data/reminders-service.json",
//                "target/test-data/reminder-stats-service.json",
//                STATS_PATH
//        );
//    }
//
//    private GitHubClient mockGitHubClient(String content) {
//        GitHubClient gitHubClient = mock(GitHubClient.class);
//        when(gitHubClient.isConfigured()).thenReturn(true);
//        when(gitHubClient.fetchContent()).thenReturn(new GitHubClient.GitHubFileContent(content, "sha"));
//        return gitHubClient;
//    }
}
