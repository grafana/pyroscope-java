ARG IMAGE_VERSION
ARG JAVA_VERSION

FROM alpine:3.24.3@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a AS builder
ARG JAVA_VERSION
RUN if [ "${JAVA_VERSION}" -ge 25 ] 2>/dev/null; then \
        apk add openjdk17; \
    else \
        apk add openjdk11; \
    fi

WORKDIR /app
ADD gradlew build.gradle settings.gradle gradle.properties /app/
ADD gradle gradle
RUN if [ "${JAVA_VERSION}" -ge 25 ] 2>/dev/null; then ./gradlew --no-daemon wrapper --gradle-version=9.0; fi
RUN ./gradlew --no-daemon --version
ADD agent agent
ADD async-profiler-grafana-fork-dist async-profiler-grafana-fork-dist
ADD demo/build.gradle demo/

RUN ./gradlew --no-daemon shadowJar

FROM alpine:${IMAGE_VERSION} AS runner
ARG IMAGE_VERSION
ARG JAVA_VERSION
RUN apk add --no-cache curl && \
    curl -fsSL https://cdn.azul.com/public_keys/alpine-signing@azul.com-5d5dc44c.rsa.pub -o /etc/apk/keys/alpine-signing@azul.com-5d5dc44c.rsa.pub && \
    echo "https://repos.azul.com/zulu/alpine" >> /etc/apk/repositories && \
    apk update && \
    apk add zulu${JAVA_VERSION}-jdk

WORKDIR /app
ADD demo demo
COPY --from=builder /app/agent/build/libs/pyroscope.jar /app/agent/build/libs/pyroscope.jar

RUN javac demo/src/main/java/Fib.java

ENV PYROSCOPE_LOG_LEVEL=debug
ENV PYROSCOPE_SERVER_ADDRESS=http://pyroscope:4040
ENV PYROSCOPE_APPLICATION_NAME=alpine-${IMAGE_VERSION}-${JAVA_VERSION}
ENV PYROSCOPE_UPLOAD_INTERVAL=15s

CMD ["java", "-javaagent:/app/agent/build/libs/pyroscope.jar", "-cp", "demo/src/main/java/", "Fib"]
