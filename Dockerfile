FROM eclipse-temurin:8-jre-jammy

WORKDIR /app

RUN groupadd --system notice \
    && useradd --system --gid notice --home-dir /app --shell /usr/sbin/nologin notice \
    && mkdir -p /app/logs /app/data \
    && chown -R notice:notice /app

COPY --chown=notice:notice target/notice-0.0.1-SNAPSHOT.jar /app/notice.jar

ENV TZ=Asia/Shanghai
ENV SERVER_PORT=8080
ENV NOTICE_LOG_FILE=/app/logs/notice.log
ENV NOTICE_LOG_FILE_PATTERN=/app/logs/notice.%d{yyyy-MM-dd}.log
ENV NOTICE_WEB_STORAGE_PATH=/app/data/reminders.json
ENV NOTICE_WEB_STATS_STORAGE_PATH=/app/data/reminder-stats.json
ENV JAVA_TOOL_OPTIONS="-Dsun.io.useCanonCaches=false"

EXPOSE 8080

USER notice

ENTRYPOINT ["java", "-jar", "/app/notice.jar"]
