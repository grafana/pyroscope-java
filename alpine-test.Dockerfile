ARG IMAGE_VERSION
FROM alpine:${IMAGE_VERSION}
ARG IMAGE_VERSION
ARG JAVA_VERSION
RUN apk add openjdk${JAVA_VERSION}
WORKDIR /app
ADD gradlew build.gradle settings.gradle /app/
ADD gradle gradle
RUN ./gradlew --no-daemon --version
ADD agent agent
ADD async-profiler-context async-profiler-context
ADD demo/build.gradle demo/

RUN ./gradlew --no-daemon shadowJar

ADD demo demo

ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/jvm/default-jvm/jre/bin


RUN javac demo/src/main/java/Fib.java

ENV PYROSCOPE_LOG_LEVEL=debug
ENV PYROSCOPE_SERVER_ADDRESS=http://pyroscope:4040
ENV PYROSCOPE_APPLICATION_NAME=alpine-${IMAGE_VERSION}-${JAVA_VERSION}
ENV PYROSCOPE_UPLOAD_INTERVAL=15s
CMD ["java", "-javaagent:/app/agent/build/libs/pyroscope.jar", "-cp", "demo/src/main/java/", "Fib"]
