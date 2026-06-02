# notice

`notice` 是一个 Spring Boot 定时通知服务。服务会定时从 GitHub 仓库读取指定 JSON 文件，解析其中的任务数组，根据每条任务的 `cron`/`corn` 表达式判断是否需要在当前外层任务启动时间发送消息，然后通过企业微信机器人 webhook 发送文本通知。

## 项目结构

```text
notice
├── Dockerfile
├── pom.xml
├── README.md
└── src/main
    ├── java/com/jkoi/notice
    │   ├── NoticeApplication.java
    │   ├── client
    │   │   ├── GitHubClient.java
    │   │   └── WeComWebhookClient.java
    │   ├── config
    │   │   ├── GitHubProperties.java
    │   │   ├── NoticeProperties.java
    │   │   └── WeComProperties.java
    │   ├── controller
    │   │   └── SystemController.java
    │   ├── service
    │   │   ├── ScheduledFactory.java
    │   │   ├── ScheduledService.java
    │   │   └── impl
    │   │       └── ScheduleTodayReminderImpl.java
    │   ├── task
    │   │   └── GitHubNoticeScheduler.java
    │   └── util
    │       └── DateUtil.java
    └── resources
        └── application.yml
```

## 核心模块

| 模块 | 说明 |
| --- | --- |
| `NoticeApplication` | Spring Boot 启动入口，开启配置属性绑定和定时任务。 |
| `GitHubNoticeScheduler` | 核心调度器。每分钟执行一次，读取 GitHub 配置文件，筛选当前时间命中的任务并发送通知。 |
| `GitHubClient` | GitHub HTTP 客户端。支持 GitHub REST API 地址，也支持 `https://github.com/owner/repo.git` 仓库地址自动转换为 contents API。 |
| `WeComWebhookClient` | 企业微信机器人客户端，发送 `text` 类型消息，并检查 webhook 返回错误码。 |
| `NoticeProperties` | `notice.*` 配置项绑定。 |
| `GitHubProperties` | `github.*` 配置项绑定。 |
| `WeComProperties` | `wecom.*` 配置项绑定。 |
| `SystemController` | 简单 HTTP 健康检查接口，当前 `/` 返回 `success`。 |
| `ScheduledFactory` | 内置任务工厂，根据 `exeCode` 查找对应的 `ScheduledService` 实现。 |
| `ScheduledService` | 内置任务扩展接口。 |
| `ScheduleTodayReminderImpl` | 示例内置任务，`exeCode` 为 `TODAY_REMINDER`，每次执行都会访问网页抓取今日/天气信息并发送企业微信通知。 |
| `DateUtil` | 日期时间工具类。 |

## 执行流程

1. Spring 定时任务按 `@Scheduled(cron = "0 0/1 * * * ?")` 每分钟启动一次。
2. 记录本次外层任务启动时间，精确到分钟。
3. `GitHubClient` 使用 `GITHUB_TOKEN` 拉取 GitHub 仓库中的指定文件。
4. 如果使用 GitHub contents API，客户端会自动解码返回的 Base64 文件内容。
5. 文件内容应是 JSON 数组。
6. 系统遍历数组中的每个对象，读取 `cron` 或 `corn` 表达式。
7. 如果外层任务启动时间符合该表达式：
   - 有 `exeCode`：调用对应的内置任务。
   - 没有 `exeCode`：读取 `data` 字段并发送企业微信文本消息。

## GitHub 文件格式

文件内容需要是 JSON 数组：

```json
[
  {
    "cron": "0 9 * * * ?",
    "data": "早上好"
  },
  {
    "corn": "0 18 * * * ?",
    "data": {
      "title": "下班提醒",
      "content": "记得提交日报"
    }
  },
  {
    "cron": "0 8 * * * ?",
    "exeCode": "TODAY_REMINDER"
  }
]
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `cron` / `corn` | 否 | Quartz cron 表达式。不填时默认命中。当前外层任务每分钟执行一次，所以通常秒字段写 `0`。 |
| `data` | 否 | 要发送的文本内容。字符串直接发送；对象或数组会序列化为 JSON 字符串发送。 |
| `exeCode` | 否 | 内置任务编码。当前支持 `TODAY_REMINDER`。存在时优先执行内置任务，不读取 `data`。 |

## 环境变量

必填：

| 环境变量 | 说明 |
| --- | --- |
| `GITHUB_TOKEN` | GitHub token。 |
| `GITHUB_API_URL` | GitHub API 或仓库地址，例如 `https://github.com/openwjk/cli-config.git`。 |
| `WECOM_WEBHOOK_URL` | 企业微信机器人 webhook 地址。 |

可选：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `GITHUB_FILE_PATH` | `/notice/notice.json` | 仓库内要读取的文件路径。 |
| `GITHUB_REF` | `master` | 分支、tag 或 commit SHA。 |
| `GITHUB_ACCEPT` | `application/vnd.github+json` | GitHub API `Accept` 请求头。 |
| `GITHUB_API_VERSION` | `2022-11-28` | GitHub API 版本请求头。 |
| `GITHUB_TIMEOUT_MS` | `10000` | GitHub 请求超时时间，单位毫秒。 |
| `WECOM_TIMEOUT_MS` | `10000` | 企业微信 webhook 请求超时时间，单位毫秒。 |
| `NOTICE_ENABLED` | `true` | 是否启用定时任务。 |
| `NOTICE_CRON_FIELD` | `corn` | 自定义 cron 字段名；代码也会兜底读取 `cron`。 |
| `NOTICE_DATA_FIELD` | `data` | 自定义数据字段名。 |
| `NOTICE_MAX_CONTENT_LENGTH` | `3000` | 企业微信文本最大截断长度。 |

## 本地运行

使用 IDEA 自带 Maven 或本机 Maven 构建：

```bash
mvn clean package
```

Windows PowerShell 示例：

```powershell
$env:GITHUB_TOKEN="ghp_xxx"
$env:GITHUB_API_URL="https://github.com/openwjk/cli-config.git"
$env:GITHUB_FILE_PATH="/notice/notice.json"
$env:GITHUB_REF="master"
$env:WECOM_WEBHOOK_URL="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
java -jar target\notice-0.0.1-SNAPSHOT.jar
```

Linux/macOS 示例：

```bash
export GITHUB_TOKEN="ghp_xxx"
export GITHUB_API_URL="https://github.com/openwjk/cli-config.git"
export GITHUB_FILE_PATH="/notice/notice.json"
export GITHUB_REF="master"
export WECOM_WEBHOOK_URL="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
java -jar target/notice-0.0.1-SNAPSHOT.jar
```

## HTTP 检查

服务默认监听 `server.port=80`。

```bash
curl http://localhost/
```

返回：

```text
success
```

## Docker

先构建 jar：

```bash
mvn clean package
```

构建镜像：

```bash
docker build -t notice:latest .
```

如果 Docker Hub 不可访问，可以指定可访问的 Java 8 JRE 基础镜像：

```bash
docker build --build-arg BASE_IMAGE=m.daocloud.io/docker.io/eclipse-temurin:8-jre-jammy -t notice:latest .
docker build --build-arg BASE_IMAGE=your-registry/your-java8-jre:tag -t notice:latest .
```

运行容器：

```bash
docker run -d --name notice \
  -p 80:80 \
  -e GITHUB_TOKEN="ghp_xxx" \
  -e GITHUB_API_URL="https://github.com/openwjk/cli-config.git" \
  -e GITHUB_FILE_PATH="/notice/notice.json" \
  -e GITHUB_REF="master" \
  -e WECOM_WEBHOOK_URL="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx" \
  notice:latest
```

## 扩展内置任务

新增一个类实现 `ScheduledService`：

```java
@Service
public class MyScheduledService implements ScheduledService {
    @Override
    public String getCode() {
        return "MY_TASK";
    }

    @Override
    public void execute(Date date) {
        // task logic
    }
}
```

然后在 GitHub JSON 文件中配置：

```json
[
  {
    "cron": "0 10 * * * ?",
    "exeCode": "MY_TASK",
    "data": "data"
  }
]
```

## 注意事项

- `GITHUB_TOKEN` 和 `WECOM_WEBHOOK_URL` 都是敏感信息，不要提交到代码仓库。
- 当前外层任务每分钟执行一次，因此建议配置文件中的 cron 秒字段使用 `0`。
- `TODAY_REMINDER` 每次执行都会通过网页请求抓取信息；如果目标网页结构变化或网络不可达，可能只能发送获取失败提示。
- 如果需要让 `/notice/` 也作为健康检查接口，需要在 `SystemController` 中增加对应映射。
# notice
