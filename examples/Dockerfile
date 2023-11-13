FROM openjdk:11.0.11-jdk

ADD https://download.jboss.org/optaplanner/release/8.6.0.Final/optaweb-employee-rostering-distribution-8.6.0.Final.zip /

RUN apt-get update && apt-get install -y unzip \
    && rm -rf /var/lib/apt/lists/*

RUN unzip optaweb-employee-rostering-distribution-8.6.0.Final.zip && \
    rm optaweb-employee-rostering-distribution-8.6.0.Final.zip

ADD pyroscope.jar /

CMD ["java", "-javaagent:/pyroscope.jar", "-jar", "/optaweb-employee-rostering-distribution-8.6.0.Final/bin/optaweb-employee-rostering-standalone-8.6.0.Final/quarkus-run.jar"]
