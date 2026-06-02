FROM 127.0.0.1:5000/eclipse-temurin:latest

WORKDIR /app

COPY target/notice-0.0.1-SNAPSHOT.jar /app/notice.jar

ENV TZ=Asia/Shanghai

EXPOSE 80

ENTRYPOINT ["java", "-jar", "/app/notice.jar"]
