FROM alpine:3.23.3@sha256:25109184c71bdad752c8312a8623239686a9a2071e8825f20acb8f2198c3f659 AS builder
RUN apk add openjdk11

WORKDIR /app
ADD gradlew build.gradle settings.gradle gradle.properties /app/
ADD gradle gradle
RUN ./gradlew --no-daemon --version
ADD agent agent
ADD async-profiler-grafana-fork-dist async-profiler-grafana-fork-dist
ADD demo/build.gradle demo/

COPY itest/bootstrap/BootstrapCheck.java /app/itest/bootstrap/BootstrapCheck.java
RUN ./gradlew --no-daemon shadowJar
RUN javac itest/bootstrap/BootstrapCheck.java

FROM alpine:3.23.3@sha256:25109184c71bdad752c8312a8623239686a9a2071e8825f20acb8f2198c3f659 AS runner
RUN apk add openjdk11-jre-headless

WORKDIR /app
COPY --from=builder /app/agent/build/libs/pyroscope.jar /app/pyroscope.jar
COPY --from=builder /app/itest/bootstrap/BootstrapCheck.class /app/BootstrapCheck.class

ENV PYROSCOPE_AGENT_ENABLED=false

CMD ["java", "-javaagent:/app/pyroscope.jar", "BootstrapCheck"]
