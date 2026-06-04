# notice

`notice` 是一个 Spring Boot 定时通知服务。服务会定时从 GitHub 仓库读取指定 JSON 文件，解析其中的任务数组，根据每条任务的 `cron` / `corn` 表达式判断是否需要在当前外层任务启动时间发送消息，然后通过企业微信机器人 webhook 发送文本通知。

当前项目已经支持：
- 从 GitHub 动态读取通知配置
- 发送普通文本通知到企业微信
- 执行内置任务
- `TODAY_REMINDER` 内置任务中获取腾讯天气
- `TODAY_REMINDER` 内置任务中计算最近节假日倒计时
- `FESTIVAL_REMINDER` 内置任务中根据节假日日历提醒补班或放假

## 项目结构

```text
notice
├── Dockerfile
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/jkoi/notice
    │   │   ├── NoticeApplication.java
    │   │   ├── client
    │   │   │   ├── GitHubClient.java
    │   │   │   └── WeComWebhookClient.java
    │   │   ├── config
    │   │   │   ├── GitHubProperties.java
    │   │   │   ├── NoticeProperties.java
    │   │   │   └── WeComProperties.java
    │   │   ├── controller
    │   │   │   └── SystemController.java
    │   │   ├── service
    │   │   │   ├── ScheduledFactory.java
    │   │   │   ├── ScheduledService.java
    │   │   │   └── impl
    │   │   │       ├── ScheduleFestivalReminderImpl.java
    │   │   │       └── ScheduleTodayReminderImpl.java
    │   │   ├── task
    │   │   │   └── GitHubNoticeScheduler.java
    │   │   └── util
    │   │       └── DateUtil.java
    │   └── resources
    │       └── application.yml
    └── test
        └── java/com/jkoi/notice/service/impl
            └── ScheduleFestivalReminderImplTest.java
```

## 核心模块

| 模块 | 说明 |
| --- | --- |
| `NoticeApplication` | Spring Boot 启动入口，开启配置属性绑定和定时任务。 |
| `GitHubNoticeScheduler` | 核心调度器。每分钟执行一次，读取 GitHub 配置文件，筛选当前时间命中的任务并发送通知。 |
| `GitHubClient` | GitHub HTTP 客户端。支持 GitHub REST API 地址，也支持 `https://github.com/owner/repo.git` 仓库地址自动转换为 contents API。 |
| `WeComWebhookClient` | 企业微信机器人客户端，发送 `text` 类型消息，并检查 webhook 返回错误码。 |
| `ScheduledFactory` | 内置任务工厂，根据 `exeCode` 查找对应的 `ScheduledService` 实现。 |
| `ScheduledService` | 内置任务扩展接口。 |
| `ScheduleTodayReminderImpl` | 内置任务，当前 `getCode()` 返回 `TODAY_REMINDER`。会发送今天日期、星期、腾讯天气，以及最近节假日倒计时。 |
| `ScheduleFestivalReminderImpl` | 内置任务，读取节假日 iCal 日历，在需要时提醒补班或放假。 |
| `SystemController` | 简单 HTTP 健康检查接口，当前 `/` 返回 `success`。 |

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
    "cron": "0 8 * * * ?",
    "exeCode": "TODAY_REMINDER"
  },
  {
    "cron": "0 18 * * * ?",
    "exeCode": "FESTIVAL_REMINDER"
  }
]
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `cron` | 否 | Quartz cron 表达式。不填时默认命中。当前外层任务每分钟执行一次，所以通常秒字段写 `0`。 |
| `data` | 否 | 要发送的文本内容。字符串直接发送；对象或数组会序列化为 JSON 字符串发送。 |
| `exeCode` | 否 | 内置任务编码。当前常用值包括 `TODAY_REMINDER`、`FESTIVAL_REMINDER`。存在时优先执行内置任务，不读取 `data`。 |

## TODAY_REMINDER 说明

`TODAY_REMINDER` 当前会输出以下内容：
- 今天日期
- 星期
- 腾讯天气
- 距离最近节假日还有多少天

其中节假日倒计时规则为：
- 通过节假日日历接口获取节日列表
- 找到距离今天最近的一个节假日
- 生成 `距离最近的XX还有XX天`
- 如果今天本身就是节假日，则不追加这句提示

天气接口当前按腾讯返回的以下结构解析：

```json
{
  "status": 0,
  "message": "Success",
  "result": {
    "realtime": [
      {
        "province": "上海市",
        "city": "上海市",
        "district": "浦东新区",
        "adcode": 310115,
        "update_time": "2026-06-04 10:50",
        "infos": {
          "weather": "阴",
          "temperature": 25,
          "wind_direction": "东南风",
          "wind_power": "1-2级",
          "wind_power_v2": "1级",
          "humidity": 87,
          "air_pressure": 1002
        }
      }
    ]
  }
}
```

## 腾讯天气签名说明

腾讯天气接口当前使用以下参数：
- `key`
- `adcode`
- `sig`

签名规则：
1. 对请求参数按参数名升序排列。
2. 使用未进行 URL 编码的原始参数值拼接查询串。
3. 用 `请求路径 + "?" + 原始查询串 + SecretKey` 计算 MD5。
4. 实际发送请求时，对参数值逐个 URL 编码，再追加 `sig`。

当前代码使用的路径是：
- `/ws/weather/v1/`

## 环境变量

必填：

| 环境变量 | 说明 |
| --- | --- |
| `GITHUB_TOKEN` | GitHub token。 |
| `GITHUB_API_URL` | GitHub API 或仓库地址，例如 `https://github.com/openwjk/cli-config.git`。 |
| `WECOM_WEBHOOK_URL` | 企业微信机器人 webhook 地址。 |
| `TENCENT_KEY` | 腾讯位置服务 WebService Key。 |
| `TENCENT_SECRET_KEY` | 腾讯位置服务 WebService Secret Key，用于计算 `sig`。 |

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
| `TENCENT_ADCODE` | `310115` | 腾讯天气接口使用的行政区划代码。 |
| `TENCENT_TIMEOUT_MS` | `10000` | 腾讯天气接口超时时间，单位毫秒。 |
| `FESTIVAL_CALENDAR_URL` | `https://ical.muhan.org` | 节假日日历地址。 |
| `FESTIVAL_TIMEOUT_MS` | `10000` | 节假日日历请求超时时间，单位毫秒。 |

## application.yml 说明

当前配置文件中已经包含：

```yaml
tencent:
  key: ${TENCENT_KEY:...}
  secret-key: ${TENCENT_SECRET_KEY:...}
  adcode: ${TENCENT_ADCODE:310115}
  timeout-ms: ${TENCENT_TIMEOUT_MS:10000}

festival:
  calendar-url: ${FESTIVAL_CALENDAR_URL:https://ical.muhan.org}
  timeout-ms: ${FESTIVAL_TIMEOUT_MS:10000}
```

建议实际部署时通过环境变量注入 `TENCENT_KEY` 与 `TENCENT_SECRET_KEY`，不要依赖默认值。

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
$env:TENCENT_KEY="your_key"
$env:TENCENT_SECRET_KEY="your_secret"
$env:TENCENT_ADCODE="310115"
java -jar target\notice-0.0.1-SNAPSHOT.jar
```

Linux/macOS 示例：

```bash
export GITHUB_TOKEN="ghp_xxx"
export GITHUB_API_URL="https://github.com/openwjk/cli-config.git"
export GITHUB_FILE_PATH="/notice/notice.json"
export GITHUB_REF="master"
export WECOM_WEBHOOK_URL="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
export TENCENT_KEY="your_key"
export TENCENT_SECRET_KEY="your_secret"
export TENCENT_ADCODE="310115"
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
  -e TENCENT_KEY="your_key" \
  -e TENCENT_SECRET_KEY="your_secret" \
  -e TENCENT_ADCODE="310115" \
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

- `GITHUB_TOKEN`、`WECOM_WEBHOOK_URL`、`TENCENT_KEY`、`TENCENT_SECRET_KEY` 都是敏感信息，不要提交到代码仓库。
- 当前外层任务每分钟执行一次，因此建议配置文件中的 cron 秒字段使用 `0`。
- 腾讯天气接口签名对参数名、路径、原始参数值是否编码都比较敏感，修改时要同步验证 `sig`。
- 如果腾讯接口返回 `status=120`，表示当前 key 被限流。
- `TODAY_REMINDER` 当前的任务编码是 `TODAY_REMINDER`，如果 GitHub 配置里仍写 `TODAY_REMINDER`，将不会命中。
- 如果需要让 `/notice/` 也作为健康检查接口，需要在 `SystemController` 中增加对应映射。