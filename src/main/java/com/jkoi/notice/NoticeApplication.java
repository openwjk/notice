package com.jkoi.notice;

import com.jkoi.notice.config.GitHubProperties;
import com.jkoi.notice.config.NoticeProperties;
import com.jkoi.notice.config.WeComProperties;
import com.jkoi.notice.config.WechatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({
        NoticeProperties.class,
        GitHubProperties.class,
        WeComProperties.class,
        WechatProperties.class
})
public class NoticeApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoticeApplication.class, args);
    }
}
