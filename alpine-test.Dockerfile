ARG IMAGE_VERSION=3.16
FROM alpine:${IMAGE_VERSION}
RUN apk add openjdk11
WORKDIR /app
ADD gradlew build.gradle settings.gradle /app/
ADD gradle gradle
RUN ./gradlew --no-daemon --version
ADD agent agent
ADD async-profiler-context async-profiler-context
add demo/build.gradle demo/

RUN ./gradlew --no-daemon shadowJar

ADD demo demo

RUN /usr/lib/jvm/default-jvm/jre/bin/javac demo/src/main/java/Fib.java

ENV PYROSCOPE_LOG_LEVEL=debug
ENV PYROSCOPE_SERVER_ADDRESS=http://pyroscope:4040
ARG IMAGE_VERSION=3.16
ENV PYROSCOPE_APPLICATION_NAME=alpine-${IMAGE_VERSION}

CMD ["java", "-javaagent:/app/agent/build/libs/pyroscope.jar", "-cp", "demo/src/main/java/", "Fib"]
