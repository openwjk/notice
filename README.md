# notice

`notice` 是一个基于 Spring Boot 的个人提醒系统。后端负责从 GitHub 或本地文件读取提醒配置，按 Quartz Cron 每分钟匹配一次任务，并通过企业微信机器人发送提醒；微信小程序负责查看看板、管理提醒配置、测试发送、查看运行日志。

当前系统只有两类提醒：

- 普通文本：命中 Cron 后直接发送配置中的文本或 JSON 内容。
- 工作流：命中 Cron 后按 `exeCode` 执行后端内置工作流，例如今日提醒、生日提醒、节假日提醒。

## 功能概览

- 从 GitHub 动态读取和保存提醒配置。
- 将统计数据写回 GitHub，支持看板展示已启用、今日命中、异常记录。
- 支持逻辑删除提醒配置，不直接物理删除 JSON 项。
- 支持普通文本和工作流两种提醒类型。
- 支持工作流执行编码从后端获取，并根据后端样例自动生成填充字段。
- 支持配置页测试发送，测试通过后才允许保存。
- 支持 Cron 后续 7 次生效时间预览。
- 支持系统日志查看、时间范围筛选、跨天查询、异常堆栈展示和日志分页。
- 后端日志按天滚动，默认保留 7 天。
- 除根接口外，后端接口要求请求头带 `wxid` 或 `X-Wx-Id`。

## 技术栈

- 后端：Java 8、Spring Boot 2.7.18、Quartz Cron、Maven
- 前端：微信小程序原生页面
- 存储：GitHub contents API 优先，本地 JSON 文件兜底
- 通知：企业微信机器人 webhook
- 日志：Logback 按天滚动

## 项目结构

```text
notice
├── Dockerfile
├── pom.xml
├── README.md
├── data/                         # 本地提醒配置与统计数据兜底目录
├── logs/                         # 运行日志目录，按天滚动
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
    │   │   │   ├── WeComProperties.java
    │   │   │   ├── WebMvcConfig.java
    │   │   │   ├── WechatProperties.java
    │   │   │   └── WxIdInterceptor.java
    │   │   ├── controller
    │   │   │   ├── ReminderConfigController.java
    │   │   │   ├── SystemController.java
    │   │   │   └── SystemLogController.java
    │   │   ├── logging
    │   │   │   └── SystemLogFileService.java
    │   │   ├── model
    │   │   │   ├── ReminderConfig.java
    │   │   │   ├── ReminderField.java
    │   │   │   ├── ReminderStatRecord.java
    │   │   │   ├── ReminderStats.java
    │   │   │   └── SystemLogEntry.java
    │   │   ├── service
    │   │   │   ├── ReminderConfigService.java
    │   │   │   ├── ScheduledFactory.java
    │   │   │   ├── ScheduledService.java
    │   │   │   ├── WechatIdentityService.java
    │   │   │   └── impl
    │   │   │       ├── ScheduledBirthdayReminderImpl.java
    │   │   │       ├── ScheduleFestivalReminderImpl.java
    │   │   │       └── ScheduleTodayReminderImpl.java
    │   │   ├── task
    │   │   │   └── GitHubNoticeScheduler.java
    │   │   └── util
    │   │       └── DateUtil.java
    │   └── resources
    │       ├── application.yml
    │       └── logback-spring.xml
    └── test
        └── java/com/jkoi/notice
            ├── controller
            │   └── ReminderConfigControllerTest.java
            └── service
                └── ReminderConfigServiceTest.java
```

`.git/`、`.idea/`、`target/` 等 Git 元数据、IDE 配置和 Maven 构建产物未在上面展开。

微信小程序是独立目录，和后端项目同级：

```text
D:\workspace\ai\notice-web
├── app.js
├── app.json
├── app.wxss
├── project.config.json
├── project.private.config.json
├── sitemap.json
├── utils/
│   └── request.js                # 请求封装，负责获取并注入 wxid
└── pages/reminders/
    ├── dashboard.*               # 看板页
    ├── list.*                    # 配置列表页
    ├── index.*                   # 配置编辑页
    └── logs.*                    # 系统日志页
```

## 后端运行

项目默认监听 `8080` 端口。

```bash
mvn test
mvn spring-boot:run
```

打包运行：

```bash
mvn clean package
java -jar target/notice-0.0.1-SNAPSHOT.jar
```

Windows PowerShell 示例：

```powershell
$env:GITHUB_TOKEN="ghp_xxx"
$env:GITHUB_API_URL="https://github.com/openwjk/cli-config.git"
$env:GITHUB_FILE_PATH="/notice/notice.json"
$env:GITHUB_STATS_FILE_PATH="/notice/notice-stats.json"
$env:GITHUB_REF="master"
$env:WECOM_WEBHOOK_URL="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
$env:TENCENT_KEY="your_tencent_key"
$env:TENCENT_SECRET_KEY="your_tencent_secret"
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/
```

返回：

```text
success
```

## 微信小程序运行

小程序代码在 `D:\workspace\ai\notice-web` 下，使用微信开发者工具打开该目录即可运行。

前端请求地址在：

```text
D:\workspace\ai\notice-web\utils\request.js
```

当前 `BASE_URL` 指向：

```text
http://192.168.1.61:8080
```

更换后端机器或端口时，需要同步修改这里。

小程序请求流程：

1. 调用 `wx.login()` 获取登录 code。
2. 请求后端根接口 `/?code=xxx` 换取 `wxid`。
3. 后续所有接口请求头自动携带 `wxid` 和 `X-Wx-Id`。
4. 如果后端返回空对象，前端会清理缓存 wxid 并重试一次。

## 重要配置

配置文件位于 `src/main/resources/application.yml`，所有敏感配置都建议通过环境变量注入。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 服务端口，Dockerfile 中也设置为 8080。 |
| `NOTICE_ENABLED` | `true` | 是否启用每分钟提醒调度。 |
| `NOTICE_CRON_FIELD` | `corn` | 自定义 Cron 字段名，系统也兼容读取 `cron`。 |
| `NOTICE_DATA_FIELD` | `data` | 普通文本默认读取的数据字段。 |
| `NOTICE_MAX_CONTENT_LENGTH` | `3000` | 企业微信文本发送前最大截断长度。 |
| `GITHUB_TOKEN` | 空 | GitHub token。 |
| `GITHUB_API_URL` | 空 | GitHub 仓库地址或 contents API 基础地址。配置后优先使用 GitHub。 |
| `GITHUB_FILE_PATH` | `/notice/notice.json` | 提醒配置文件路径。 |
| `GITHUB_STATS_FILE_PATH` | `/notice/notice-stats.json` | 统计数据文件路径。 |
| `GITHUB_REF` | `master` | 分支、tag 或 commit SHA。 |
| `GITHUB_ACCEPT` | `application/vnd.github+json` | GitHub API Accept 请求头。 |
| `GITHUB_API_VERSION` | `2022-11-28` | GitHub API 版本请求头。 |
| `GITHUB_TIMEOUT_MS` | `10000` | GitHub 请求超时时间。 |
| `WECOM_WEBHOOK_URL` | 空 | 企业微信机器人 webhook。 |
| `WECOM_TIMEOUT_MS` | `10000` | 企业微信请求超时时间。 |
| `WECHAT_APP_ID` | 空 | 微信小程序 AppId。 |
| `WECHAT_SECRET` | 空 | 微信小程序 Secret。 |
| `WECHAT_SESSION_URL` | 微信官方 jscode2session 地址 | code 换 openid 的接口。 |
| `WECHAT_TIMEOUT_MS` | `5000` | 微信身份接口超时时间。 |
| `WECHAT_DEV_FALLBACK_ENABLED` | `true` | 未配置微信密钥时，用 code 生成 `dev-` 前缀 wxid。 |
| `TENCENT_KEY` | 空 | 腾讯位置服务 WebService Key。 |
| `TENCENT_SECRET_KEY` | 空 | 腾讯位置服务 Secret Key，用于计算 `sig`。 |
| `TENCENT_ADCODE` | `310115` | 天气查询行政区划代码。 |
| `TENCENT_TIMEOUT_MS` | `10000` | 腾讯天气请求超时时间。 |
| `FESTIVAL_CALENDAR_URL` | `https://ical.muhan.org` | 节假日日历地址。 |
| `FESTIVAL_TIMEOUT_MS` | `10000` | 节假日日历请求超时时间。 |
| `NOTICE_WEB_STORAGE_PATH` | `data/reminders.json` | 本地提醒配置兜底文件。 |
| `NOTICE_WEB_STATS_STORAGE_PATH` | `data/reminder-stats.json` | 本地统计数据兜底文件。 |
| `NOTICE_LOG_FILE` | `logs/notice.log` | 当前日志文件路径。 |
| `NOTICE_LOG_FILE_PATTERN` | `logs/notice.%d{yyyy-MM-dd}.log` | 按天滚动日志文件名。 |
| `NOTICE_LOG_MAX_HISTORY` | `7` | 日志最多保留天数。 |
| `NOTICE_LOG_TOTAL_SIZE_CAP` | `1GB` | 日志总大小上限。 |
| `NOTICE_LOG_CLEAN_HISTORY_ON_START` | `true` | 启动时清理过期日志。 |

## 数据来源和存储策略

后端的提醒配置和统计数据有两种来源：

- GitHub 已配置：读取和保存到 GitHub contents API。
- GitHub 未配置：读取和保存到本地 `data/` 目录。

判断是否启用 GitHub 的条件是同时配置：

```text
GITHUB_TOKEN
GITHUB_API_URL
```

提醒配置和统计数据是两个文件：

- 提醒配置：`GITHUB_FILE_PATH`
- 统计数据：`GITHUB_STATS_FILE_PATH`

配置删除采用逻辑删除：将对应项标记为 `deleted: true` 并关闭 `enabled`，不会直接从 JSON 中物理移除。

## 提醒配置 JSON 格式

GitHub 配置文件可以是 JSON 数组，也可以是包含 `items` 数组的对象。系统保存时会写成数组。

普通文本提醒：

```json
[
  {
    "title": "水电费",
    "type": "text",
    "enabled": true,
    "cron": "0 0 9 28 * ?",
    "data": "记得今天交水电燃气费哦~"
  }
]
```

工作流提醒：

```json
[
  {
    "title": "生日提醒",
    "type": "flow",
    "enabled": true,
    "cron": "0 0 10 * * ?",
    "exeCode": "BIRTHDAY_REMINDER",
    "data": [
      { "name": "张三", "birthday": "1990-01-01" },
      { "name": "李四", "birthday": "二月廿一" }
    ]
  }
]
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `id` | 可选。为空时后端会根据内容生成合成 ID。 |
| `title` | 提醒名称。为空时后端会尝试根据类型和内容生成。 |
| `type` | `text` 或 `flow`。存在 `exeCode` 时可推断为 `flow`。 |
| `enabled` | 是否启用，默认 `true`。 |
| `deleted` | 是否逻辑删除，默认 `false`。 |
| `cron` / `corn` | Quartz Cron 表达式。系统每分钟匹配一次，通常秒字段写 `0`。 |
| `data` | 普通文本内容，或工作流需要的填充数据。 |
| `dataField` | 指定发送或解析数据字段，默认 `data`。 |
| `exeCode` | 工作流执行编码。 |

## 内置工作流

内置工作流由 `ScheduledService` 实现，并通过 `ScheduledFactory` 按 `exeCode` 注册。

| exeCode | 说明 | 样例 |
| --- | --- | --- |
| `TODAY_REMINDER` | 发送今天日期、星期、腾讯天气和最近节假日倒计时。 | `{}` |
| `FESTIVAL_REMINDER` | 根据节假日日历，提醒明天是否需要补班或关闭闹钟。 | `{}` |
| `BIRTHDAY_REMINDER` | 根据公历日期或农历生日匹配人员生日并发送提醒。 | `{"data":[{"name":"张三","birthday":"1990-01-01"}]}` |

新增工作流时，实现 `ScheduledService`：

```java
@Service
public class MyScheduledService implements ScheduledService {
    @Override
    public String getCode() {
        return "MY_WORKFLOW";
    }

    @Override
    public String getName() {
        return "我的工作流";
    }

    @Override
    public String getSample() {
        return "{\"data\":\"sample\"}";
    }

    @Override
    public void execute(Date date, JsonNode node) {
        // workflow logic
    }
}
```

`getSample()` 会返回给小程序配置页，用来自动生成工作流填充字段和预览 JSON。

## 调度流程

1. `GitHubNoticeScheduler` 每分钟执行一次。
2. 取当前时间并截断到分钟。
3. 从 `ReminderConfigService.exportSchedulerPayload()` 获取启用且未删除的提醒。
4. 对每条提醒读取 `cron` 或配置的 `NOTICE_CRON_FIELD`。
5. Cron 命中时：
   - 有 `exeCode`：执行对应 `ScheduledService`。
   - 无 `exeCode`：读取 `dataField` 或 `data` 字段发送企业微信文本。
6. 记录今日命中和异常统计。
7. 统计数据写入 GitHub 或本地统计文件。

## HTTP API

除 `/` 和 OPTIONS 请求外，所有接口都需要请求头：

```text
wxid: your-wxid
```

也兼容：

```text
X-Wx-Id: your-wxid
```

常用接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/` | 健康检查，返回 `success`。 |
| `GET` | `/?code=xxx` | 使用微信登录 code 换取 wxid。 |
| `GET` | `/api/reminders` | 获取看板数据、配置列表、统计数据和导出 JSON。 |
| `POST` | `/api/reminders` | 新增提醒配置。 |
| `PUT` | `/api/reminders/{id}` | 修改提醒配置。 |
| `DELETE` | `/api/reminders/{id}` | 逻辑删除提醒配置。 |
| `GET` | `/api/reminders/export` | 导出调度器实际使用的 JSON 数组。 |
| `GET` | `/api/reminders/exe-codes` | 获取可用工作流执行编码。 |
| `GET` | `/api/reminders/exe-codes/{code}/sample` | 获取某个工作流样例。 |
| `POST` | `/api/reminders/test` | 按测试日期走正常调度流程测试发送。 |
| `POST` | `/api/reminders/cron/preview` | 预览 Cron 后续 7 次触发时间。 |
| `GET` | `/api/system/logs` | 查询系统日志。 |

日志接口参数：

| 参数 | 说明 |
| --- | --- |
| `after` | 查询某条 sequence 之后的日志，用于实时追加。 |
| `before` | 查询某条 sequence 之前的日志，用于加载更早日志。 |
| `limit` | 单页数量，后端最大限制为 300。 |
| `level` | 日志级别：`INFO`、`WARN`、`ERROR`、`DEBUG` 或空。 |
| `start` | 开始时间，例如 `2026-06-08T00:00:00`。 |
| `end` | 结束时间，例如 `2026-06-09T23:59:00`。 |

请求示例：

```bash
curl -H "wxid: dev-test" "http://localhost:8080/api/reminders"
curl -H "wxid: dev-test" "http://localhost:8080/api/system/logs?limit=120&level=ERROR"
```

## 小程序页面说明

| 页面 | 文件 | 说明 |
| --- | --- | --- |
| 看板 | `dashboard.*` | 展示统计信息，点击已启用、今日命中、异常、文本、工作流进入对应列表。 |
| 列表 | `list.*` | 展示配置列表，支持底部分类筛选、左滑编辑/删除。统计筛选页不展示编辑和删除按钮。 |
| 配置 | `index.*` | 新增或编辑提醒。工作流类型不允许改执行编码和类型，不提供新增字段按钮。 |
| 日志 | `logs.*` | 最新日志展示在第一行，上滑到列表底部继续加载更早日志，支持时间范围筛选和异常堆栈。 |

## 日志策略

日志配置在 `logback-spring.xml` 和 `application.yml` 中：

- 当前日志：`logs/notice.log`
- 按天滚动：`logs/notice.%d{yyyy-MM-dd}.log`
- 默认保留：7 天
- 启动时清理过期日志

日志查询从日志文件读取，不从内存读取。跨天查询会合并滚动日志和当前日志，并按时间顺序生成稳定 sequence。

## Docker

构建 jar：

```bash
mvn clean package
```

构建镜像：

```bash
docker build -t notice:latest .
```

运行容器：

```bash
docker run -d --name notice \
  -p 8080:8080 \
  -v notice-logs:/app/logs \
  -v notice-data:/app/data \
  -e GITHUB_TOKEN="ghp_xxx" \
  -e GITHUB_API_URL="https://github.com/openwjk/cli-config.git" \
  -e GITHUB_FILE_PATH="/notice/notice.json" \
  -e GITHUB_STATS_FILE_PATH="/notice/notice-stats.json" \
  -e WECOM_WEBHOOK_URL="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx" \
  -e TENCENT_KEY="your_tencent_key" \
  -e TENCENT_SECRET_KEY="your_tencent_secret" \
  notice:latest
```

Dockerfile 中设置了：

```text
NOTICE_LOG_FILE=/app/logs/notice.log
NOTICE_LOG_FILE_PATTERN=/app/logs/notice.%d{yyyy-MM-dd}.log
NOTICE_WEB_STORAGE_PATH=/app/data/reminders.json
NOTICE_WEB_STATS_STORAGE_PATH=/app/data/reminder-stats.json
JAVA_TOOL_OPTIONS=-Dsun.io.useCanonCaches=false
```

镜像会提前创建 `/app/logs` 和 `/app/data`，并授权给容器内的 `notice` 用户。如果改用宿主机 bind mount，也要确保宿主机目录允许容器用户写入。`JAVA_TOOL_OPTIONS` 用于避免 Tomcat 全局 canonical file name cache 相关风险。

## 开发检查

后端测试：

```bash
mvn test
```

小程序 JS 语法检查示例：

```bash
node --check D:\workspace\ai\notice-web\pages\reminders\index.js
node --check D:\workspace\ai\notice-web\pages\reminders\logs.js
```

## 维护注意事项

- 不要提交 `GITHUB_TOKEN`、`WECOM_WEBHOOK_URL`、`TENCENT_KEY`、`TENCENT_SECRET_KEY`、`WECHAT_SECRET` 等敏感信息。
- GitHub 未配置时，系统会回退到本地 `data/` 目录；如果看板突然为空，优先检查后端启动环境是否带了 GitHub 配置。
- Cron 使用 Quartz 格式，包含秒字段，例如 `0 0 8 * * ?`。
- 工作流配置页的字段来自后端 `getSample()`，修改工作流数据结构时需要同步样例。
- `X-Notice-Token` 已移除，前后端不再使用访问 token。
- 日志页查询的是文件内容，生产环境需要确保日志目录可读且滚动策略正确。
