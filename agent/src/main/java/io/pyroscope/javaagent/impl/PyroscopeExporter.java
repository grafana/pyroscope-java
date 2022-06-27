package io.pyroscope.javaagent.impl;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.DateUtils;
import io.pyroscope.javaagent.OverfillQueue;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.Pyroscope;
import okhttp3.*;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class PyroscopeExporter implements Exporter {
    private static final long TIMEOUT = 10 * DateUtils.NANOS_PER_SECOND;//todo allow configuration

    private static final MediaType PROTOBUF = MediaType.parse("application/x-protobuf");
    private static final MediaType JFR = MediaType.parse("application/jfr");
    private static final MediaType COLLAPSED = MediaType.parse("application/collapsed");

    final Config config;
    final Logger logger;
    final OkHttpClient client;

    public PyroscopeExporter(Config config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT, TimeUnit.NANOSECONDS)
            .readTimeout(TIMEOUT, TimeUnit.NANOSECONDS)
            .callTimeout(TIMEOUT, TimeUnit.NANOSECONDS)
            .build();

    }

    @Override
    public void export(Snapshot snapshot) {
        try {
            uploadSnapshot(snapshot);
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
                logger.log(Logger.Level.DEBUG, "Upload attempt. JFR: %s, labels: %s", snapshot.data.length, labels.length);
                MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);
                bodyBuilder.addFormDataPart(
                    /* name */ "jfr",
                    /* filename */ "jfr",
                    RequestBody.create(JFR, snapshot.data)
                );
                if (labels.length > 0) {
                    bodyBuilder.addFormDataPart(
                        /* name */ "labels",
                        /* filename */ "labels",
                        RequestBody.create(PROTOBUF, labels)
                    );
                }
                requestBody = bodyBuilder.build();
            } else {
                logger.log(Logger.Level.DEBUG, "Upload attempt. collapsed: %s", snapshot.data.length);
                requestBody = RequestBody.create(COLLAPSED, snapshot.data);
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
                    logger.log(Logger.Level.ERROR, "Error uploading snapshot: %s %s", status, responseBody);
                } else {
                    success = true;
                }
            } catch (final IOException e) {
                logger.log(Logger.Level.ERROR, "Error uploading snapshot: %s", e.getMessage());
            }

            if (!success) {
                final int backoff = exponentialBackoff.error();
                logger.log(Logger.Level.DEBUG, "Backing off for %s ms", backoff);
                Thread.sleep(backoff);
            }
        }
    }

    private HttpUrl urlForSnapshot(final Snapshot snapshot) {
        long started = snapshot.started;
        long finished = started + config.uploadInterval;
        HttpUrl.Builder builder = HttpUrl.parse(config.serverAddress)
            .newBuilder()
            .addPathSegment("ingest")
            .addQueryParameter("name", nameWithStaticLabels())
            .addQueryParameter("units", snapshot.eventType.units.id)
            .addQueryParameter("aggregationType", snapshot.eventType.aggregationType.id)
            .addQueryParameter("sampleRate", Long.toString(config.profilingIntervalInHertz()))
            .addQueryParameter("from", Long.toString(started / DateUtils.NANOS_PER_SECOND))
            .addQueryParameter("until", Long.toString(finished / DateUtils.NANOS_PER_SECOND))
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
