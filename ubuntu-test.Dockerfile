ARG IMAGE_VERSION=18.04
FROM ubuntu:${IMAGE_VERSION}
RUN apt-get update && apt-get install -y openjdk-11-jdk
WORKDIR /app
ADD gradlew build.gradle settings.gradle /app/
ADD gradle gradle
RUN ./gradlew --no-daemon --version
ADD agent agent
ADD async-profiler-context async-profiler-context
add demo/build.gradle demo/

RUN ./gradlew --no-daemon shadowJar

ADD demo demo

RUN javac demo/src/main/java/Fib.java

ENV PYROSCOPE_LOG_LEVEL=debug
ENV PYROSCOPE_SERVER_ADDRESS=http://pyroscope:4040
ENV PYROSCOPE_UPLOAD_INTERVAL=5s
ARG IMAGE_VERSION=18.04
ENV PYROSCOPE_APPLICATION_NAME=ubuntu-${IMAGE_VERSION}


CMD ["java", "-javaagent:/app/agent/build/libs/pyroscope.jar", "-cp", "demo/src/main/java/", "Fib"]
