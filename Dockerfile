FROM eclipse-temurin:21-jre-jammy AS agent-downloader

# 뉴렐릭 에이전트 다운로드 전용 스테이지 (curl을 최종 이미지에 남기지 않기 위함)
ARG NR_AGENT_VERSION=9.1.0
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    curl -fSL https://download.newrelic.com/newrelic/java-agent/newrelic-agent/${NR_AGENT_VERSION}/newrelic-agent-${NR_AGENT_VERSION}.jar -o /newrelic.jar && \
    rm -rf /var/lib/apt/lists/*

FROM eclipse-temurin:21-jre-jammy

RUN useradd --system --no-create-home --shell /usr/sbin/nologin azit

WORKDIR /app

COPY --chown=azit:azit --from=agent-downloader /newrelic.jar /newrelic.jar

# 빌드 시 생성된 jar 파일을 컨테이너 내부로 복사
# (build.gradle.kts에서 plain jar 생성을 꺼서 build/libs 밑에 실행 가능한 jar 하나만 남는다)
ARG JAR_FILE=build/libs/*.jar
COPY --chown=azit:azit ${JAR_FILE} app.jar

USER azit

# --spring.batch.job.name=... baseDate=... 같은 잡 실행 인자를 컨테이너 실행 인자로 전달.
# exec로 java를 PID 1로 올려야 종료 시그널이 셸이 아닌 JVM에 바로 전달되어 graceful shutdown이 동작한다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -javaagent:/newrelic.jar -jar app.jar \"$@\"", "--"]
