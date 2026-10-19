# 维度8 STAB-08：FSSC 容器化部署
# 构建：docker build -t fssc-server:1.0.0 -f docker/Dockerfile .
# 运行：docker run -d -p 8089:8089 -e FSSC_DB_PASSWORD=xxx -v fssc-logs:/app/logs fssc-server:1.0.0
FROM eclipse-temurin:26-jre-alpine

RUN mkdir -p /app/logs/heapdump && addgroup -S fssc && adduser -S fssc -G fssc
WORKDIR /app
COPY fssc-server/fssc-start/target/fssc-server.jar /app/fssc-start.jar

USER fssc
EXPOSE 8089
ENV JAVA_OPTS="-Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump/ -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/fssc-start.jar"]
