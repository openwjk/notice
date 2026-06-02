package com.jkoi.notice.service.impl;

import com.jkoi.notice.client.WeComWebhookClient;
import com.jkoi.notice.service.ScheduledService;
import com.jkoi.notice.util.DateUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class ScheduleTodayReminderImpl implements ScheduledService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTodayReminderImpl.class);
    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/125.0 Safari/537.36";

    private final WeComWebhookClient weComWebhookClient;

    public ScheduleTodayReminderImpl(WeComWebhookClient weComWebhookClient) {
        this.weComWebhookClient = weComWebhookClient;
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
        List<String> lines = new ArrayList<String>();
        lines.add("今天: " + DateUtil.formatDate(date, DateUtil.FORMAT_DATE_NORMAL));
        lines.add("星期: " + formatWeekday(date));
        lines.addAll(fetchBaiduSnippets("今天", "今日信息"));
        lines.addAll(fetchBaiduSnippets("天气", "天气信息"));
        return String.join("\n", lines);
    }

    private List<String> fetchBaiduSnippets(String keyword, String title) {
        List<String> snippets = new ArrayList<String>();
        try {
            Document document = Jsoup.connect(buildBaiduSearchUrl(keyword))
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .ignoreHttpErrors(true)
                    .get();

            Elements resultBlocks = document.select("#content_left .result, #content_left .c-container");
            for (Element block : resultBlocks) {
                String text = cleanup(block.text());
                if (StringUtils.hasText(text)) {
                    snippets.add(title + ": " + limit(text, 120));
                }
                if (snippets.size() >= 2) {
                    break;
                }
            }
            if (snippets.isEmpty()) {
                String bodyText = cleanup(document.body() == null ? "" : document.body().text());
                if (StringUtils.hasText(bodyText)) {
                    snippets.add(title + ": " + limit(bodyText, 120));
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch Baidu page for keyword '{}'.", keyword, ex);
            snippets.add(title + ": 网页获取失败");
        }
        return snippets;
    }

    private String buildBaiduSearchUrl(String keyword) throws IOException {
        return "https://www.baidu.com/s?wd=" + URLEncoder.encode(keyword, "UTF-8");
    }

    private String cleanup(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String limit(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String formatWeekday(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        String[] weekdays = {
                "星期日",
                "星期一",
                "星期二",
                "星期三",
                "星期四",
                "星期五",
                "星期六"
        };
        return weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1];
    }
}
