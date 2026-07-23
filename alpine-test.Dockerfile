ARG IMAGE_VERSION
ARG JAVA_VERSION

FROM alpine:3.23.3@sha256:25109184c71bdad752c8312a8623239686a9a2071e8825f20acb8f2198c3f659 AS builder
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
ADD async-profiler-genuine-dist async-profiler-genuine-dist
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
# A non-bundled libasyncProfiler at a fixed path, used to test PYROSCOPE_AP_LIBRARY_PATH
COPY --from=builder /app/async-profiler-genuine-dist/lib /app/async-profiler-genuine-dist/lib
RUN case "$(uname -m)" in \
        x86_64) cp /app/async-profiler-genuine-dist/lib/libasyncProfiler-linux-x64.so /app/libasyncProfiler.so ;; \
        aarch64) cp /app/async-profiler-genuine-dist/lib/libasyncProfiler-linux-arm64.so /app/libasyncProfiler.so ;; \
        *) echo "unsupported arch $(uname -m)" && exit 1 ;; \
    esac

RUN javac demo/src/main/java/Fib.java

ENV PYROSCOPE_LOG_LEVEL=debug
ENV PYROSCOPE_SERVER_ADDRESS=http://pyroscope:4040
ENV PYROSCOPE_APPLICATION_NAME=alpine-${IMAGE_VERSION}-${JAVA_VERSION}
ENV PYROSCOPE_UPLOAD_INTERVAL=15s

CMD ["java", "-javaagent:/app/agent/build/libs/pyroscope.jar", "-cp", "demo/src/main/java/", "Fib"]
