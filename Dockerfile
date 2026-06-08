FROM eclipse-temurin:8-jre-jammy

WORKDIR /app

RUN groupadd --system notice \
    && useradd --system --gid notice --home-dir /app --shell /usr/sbin/nologin notice

COPY --chown=notice:notice target/notice-0.0.1-SNAPSHOT.jar /app/notice.jar

ENV TZ=Asia/Shanghai
ENV SERVER_PORT=8080
ENV JAVA_TOOL_OPTIONS="-Dsun.io.useCanonCaches=false"

EXPOSE 8080

USER notice

ENTRYPOINT ["java", "-jar", "/app/notice.jar"]
