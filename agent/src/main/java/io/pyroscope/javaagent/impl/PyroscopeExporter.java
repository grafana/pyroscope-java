package io.pyroscope.javaagent.impl;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.OverfillQueue;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.Pyroscope;
import okhttp3.*;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class PyroscopeExporter implements Exporter {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);//todo allow configuration

    private static final MediaType PROTOBUF = MediaType.parse("application/x-protobuf");

    final Config config;
    final Logger logger;
    final OkHttpClient client;
    final OverfillQueue<Snapshot> queue;
    private final Thread thread;

    public PyroscopeExporter(Config config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.queue  = new OverfillQueue<>(config.pushQueueCapacity);;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT)
            .readTimeout(TIMEOUT)
            .callTimeout(TIMEOUT)
            .build();

        this.thread = new Thread(this::uploadLoop);
        this.thread.start();
    }

    private void uploadLoop() {
        logger.debug("Uploading started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final Snapshot snapshot = queue.take();
                uploadSnapshot(snapshot);
            }
        } catch (final InterruptedException e) {
            logger.debug("Uploading interrupted");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void export(Snapshot snapshot) {
        try {
            queue.put(snapshot);
        } catch (final InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }


    private void uploadSnapshot(final Snapshot snapshot) throws InterruptedException {
        final HttpUrl url = urlForSnapshot(snapshot);
        final ExponentialBackoff exponentialBackoff = new ExponentialBackoff(1_000, 30_000, new Random());
        boolean success = false;
        while (!success) {
            final RequestBody requestBody;
            if (config.format == Format.JFR) {
                byte[] labels = snapshot.labels.toByteArray();
                logger.debug("Upload attempt. JFR: {}, labels: {}", snapshot.data.length, labels.length);
                MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);
                bodyBuilder.addFormDataPart(
                    /* name */ "jfr",
                    /* filename */ "jfr",
                    RequestBody.create(snapshot.data)
                );
                if (labels.length > 0) {
                    bodyBuilder.addFormDataPart(
                        /* name */ "labels",
                        /* filename */ "labels",
                        RequestBody.create(labels, PROTOBUF)
                    );
                }
                requestBody = bodyBuilder.build();
            } else {
                logger.debug("Upload attempt. collapsed: {}", snapshot.data.length);
                requestBody = RequestBody.create(snapshot.data);
            }
            Request.Builder request = new Request.Builder()
                .post(requestBody)
                .url(url);
            if (config.authToken != null && !config.authToken.isEmpty()) {
                request.header("Authorization", "Bearer " + config.authToken);
            }
            try (Response response = client.newCall(request.build()).execute()) {
                int status = response.code();
                if (status >= 400) {
                    ResponseBody body = response.body();
                    final String responseBody;
                    if (body == null) {
                        responseBody = "";
                    } else {
                        responseBody = body.string();
                    }
                    logger.error("Error uploading snapshot: {} {}", status, responseBody);
                } else {
                    success = true;
                }
            } catch (final IOException e) {
                logger.error("Error uploading snapshot: {}", e.getMessage());
            }

            if (!success) {
                final int backoff = exponentialBackoff.error();
                logger.debug("Backing off for {} ms", backoff);
                Thread.sleep(backoff);
            }
        }
    }

    private HttpUrl urlForSnapshot(final Snapshot snapshot) {
        Instant started = snapshot.started;
        Instant finished = started.plus(config.uploadInterval);
        HttpUrl.Builder builder = HttpUrl.parse(config.serverAddress)
            .newBuilder()
            .addPathSegment("ingest")
            .addQueryParameter("name", nameWithStaticLabels())
            .addQueryParameter("units", snapshot.eventType.units.id)
            .addQueryParameter("aggregationType", snapshot.eventType.aggregationType.id)
            .addQueryParameter("sampleRate", Long.toString(config.profilingIntervalInHertz()))
            .addQueryParameter("from", Long.toString(started.getEpochSecond()))
            .addQueryParameter("until", Long.toString(finished.getEpochSecond()))
            .addQueryParameter("spyName", Config.DEFAULT_SPY_NAME);
        if (config.format == Format.JFR)
            builder.addQueryParameter("format", "jfr");
        return builder.build();
    }

    private String nameWithStaticLabels() {
        Map<String, String> labels = Pyroscope.getStaticLabels();
        if (labels.isEmpty()) {
            return config.timeseriesName;
        } else {
            StringBuilder sb = new StringBuilder(config.timeseriesName)
                .append("{");
            TreeMap<String, String> sortedMap = new TreeMap<>(labels);
            int i = 0;
            for (String key : sortedMap.keySet()) {
                if (i++ != 0) {
                    sb.append(",");
                }
                sb.append(key)
                    .append("=")
                    .append(labels.get(key));
            }
            sb.append("}");
            return sb.toString();
        }
    }

}
