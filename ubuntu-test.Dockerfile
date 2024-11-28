ARG IMAGE_VERSION
ARG JAVA_VERSION

FROM ubuntu:18.04 AS builder
RUN apt-get update && apt-get install -y openjdk-8-jdk-headless

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

FROM ubuntu:${IMAGE_VERSION} AS runner
ARG IMAGE_VERSION
ARG JAVA_VERSION
RUN apt-get update && apt-get install -y openjdk-${JAVA_VERSION}-jdk-headless

WORKDIR /app
ADD demo demo
COPY --from=builder /app/agent/build/libs/pyroscope.jar /app/agent/build/libs/pyroscope.jar

RUN javac demo/src/main/java/Fib.java

ENV PYROSCOPE_LOG_LEVEL=debug
ENV PYROSCOPE_SERVER_ADDRESS=http://pyroscope:4040
ENV PYROSCOPE_UPLOAD_INTERVAL=5s
ENV PYROSCOPE_APPLICATION_NAME=ubuntu-${IMAGE_VERSION}-${JAVA_VERSION}

CMD ["java", "-javaagent:/app/agent/build/libs/pyroscope.jar", "-cp", "demo/src/main/java/", "Fib"]
