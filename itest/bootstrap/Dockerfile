FROM alpine:3.24.3@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a AS builder
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

FROM alpine:3.24.3@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a AS runner
RUN apk add openjdk11-jre-headless

WORKDIR /app
COPY --from=builder /app/agent/build/libs/pyroscope.jar /app/pyroscope.jar
COPY --from=builder /app/itest/bootstrap/BootstrapCheck.class /app/BootstrapCheck.class

ENV PYROSCOPE_AGENT_ENABLED=false

CMD ["java", "-javaagent:/app/pyroscope.jar", "BootstrapCheck"]
