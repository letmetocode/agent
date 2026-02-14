# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

# 先复制 POM，最大化依赖缓存命中
COPY pom.xml ./pom.xml
COPY agent-api/pom.xml ./agent-api/pom.xml
COPY agent-app/pom.xml ./agent-app/pom.xml
COPY agent-domain/pom.xml ./agent-domain/pom.xml
COPY agent-infrastructure/pom.xml ./agent-infrastructure/pom.xml
COPY agent-trigger/pom.xml ./agent-trigger/pom.xml
COPY agent-types/pom.xml ./agent-types/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl agent-app -am -DskipTests dependency:go-offline

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl agent-app -am -DskipTests clean package && \
    cp agent-app/target/agent-app.jar /workspace/agent-app.jar

FROM eclipse-temurin:17-jre-jammy

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dfile.encoding=UTF-8" \
    PARAMS=""

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl tini && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 app && \
    useradd --uid 10001 --gid app --create-home --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=builder /workspace/agent-app.jar /app/agent-app.jar

RUN mkdir -p /app/data/log && chown -R app:app /app

USER app:app

EXPOSE 8091

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl --fail --silent http://127.0.0.1:8091/actuator/health > /dev/null || exit 1

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/agent-app.jar $PARAMS"]
