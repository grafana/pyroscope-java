ARG IMAGE_VERSION
ARG JAVA_VERSION

FROM alpine:3.20.3 AS builder
RUN apk add openjdk8

WORKDIR /app
ADD gradlew build.gradle settings.gradle gradle.properties /app/
ADD gradle gradle
RUN ./gradlew --no-daemon --version
ADD agent agent
ADD async-profiler-context async-profiler-context
ADD demo/build.gradle demo/

# for testing locally produced artifacts
#COPY async-profiler-3.0.0.1-linux-x64.tar.gz .
#COPY async-profiler-3.0.0.1-linux-arm64.tar.gz .
#COPY async-profiler-3.0.0.1-macos.zip .

RUN ./gradlew --no-daemon shadowJar

FROM alpine:${IMAGE_VERSION} AS runner
ARG IMAGE_VERSION
ARG JAVA_VERSION
RUN apk add openjdk${JAVA_VERSION}

WORKDIR /app
ADD demo demo
COPY --from=builder /app/agent/build/libs/pyroscope.jar /app/agent/build/libs/pyroscope.jar

ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/jvm/default-jvm/jre/bin

RUN javac demo/src/main/java/Fib.java

ENV PYROSCOPE_LOG_LEVEL=debug
ENV PYROSCOPE_SERVER_ADDRESS=http://pyroscope:4040
ENV PYROSCOPE_APPLICATION_NAME=alpine-${IMAGE_VERSION}-${JAVA_VERSION}
ENV PYROSCOPE_UPLOAD_INTERVAL=15s

CMD ["java", "-javaagent:/app/agent/build/libs/pyroscope.jar", "-cp", "demo/src/main/java/", "Fib"]
