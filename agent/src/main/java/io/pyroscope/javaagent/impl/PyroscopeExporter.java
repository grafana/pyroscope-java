package io.pyroscope.javaagent.impl;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.util.zip.GzipSink;
import io.pyroscope.labels.Pyroscope;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.zip.Deflater;

public class PyroscopeExporter implements Exporter {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);//todo allow configuration
    private static final MediaType PROTOBUF = MediaType.parse("application/x-protobuf");

    final Config config;
    final Logger logger;
    final OkHttpClient client;

    public PyroscopeExporter(Config config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT)
            .readTimeout(TIMEOUT)
            .callTimeout(TIMEOUT)
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
        boolean retry = true;
        int tries = 0;
        while (retry) {
            tries++;
            final RequestBody requestBody;
            if (config.format == Format.JFR) {
                byte[] labels = snapshot.labels.toByteArray();
                logger.log(Logger.Level.DEBUG, "Upload attempt %d. %s %s JFR: %s, labels: %s", tries,
                    snapshot.started.toString(), snapshot.ended.toString(), snapshot.data.length, labels.length);
                MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);
                RequestBody jfrBody = RequestBody.create(snapshot.data);
                if (config.compressionLevelJFR != Deflater.NO_COMPRESSION) {
                    jfrBody = GzipSink.gzip(jfrBody, config.compressionLevelJFR);
                }
                bodyBuilder.addFormDataPart("jfr", "jfr", jfrBody);
                if (labels.length > 0) {
                    RequestBody labelsBody = RequestBody.create(labels, PROTOBUF);
                    if (config.compressionLevelLabels != Deflater.NO_COMPRESSION) {
                        labelsBody = GzipSink.gzip(labelsBody, config.compressionLevelLabels);
                    }
                    bodyBuilder.addFormDataPart("labels", "labels", labelsBody);
                }
                requestBody = bodyBuilder.build();
            } else {
                logger.log(Logger.Level.DEBUG, "Upload attempt %d. collapsed: %s", tries, snapshot.data.length);
                requestBody = RequestBody.create(snapshot.data);
            }
            Request.Builder request = new Request.Builder()
                .post(requestBody)
                .url(url);

            config.httpHeaders.forEach((k, v) -> request.header(k, v));

            addAuthHeader(request, url, config);


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
                    retry = shouldRetry(status);
                } else {
                    retry = false;
                }
            } catch (final IOException e) {
                logger.log(Logger.Level.ERROR, "Error uploading snapshot: %s", e.getMessage());
            }
            if (retry) {
                if (config.ingestMaxTries >= 0 && tries >= config.ingestMaxTries) {
                    logger.log(Logger.Level.ERROR, "Gave up uploading profiling snapshot after %d tries", tries);
                    break;
                }
                final int backoff = exponentialBackoff.error();
                logger.log(Logger.Level.DEBUG, "Backing off for %s ms", backoff);
                Thread.sleep(backoff);
            }
        }
    }

    private static boolean shouldRetry(int status) {
        return status == 429 || status / 100 == 5;
    }

    private static void addAuthHeader(Request.Builder request, HttpUrl url, Config config) {
        if (config.tenantID != null && !config.tenantID.isEmpty()) {
            request.header("X-Scope-OrgID", config.tenantID);
        }
        if (config.basicAuthUser != null && !config.basicAuthUser.isEmpty()
            && config.basicAuthPassword != null && !config.basicAuthPassword.isEmpty()) {
            request.header("Authorization", Credentials.basic(config.basicAuthUser, config.basicAuthPassword));
            return;
        }
        String u = url.username();
        String p = url.password();
        if (!u.isEmpty() && !p.isEmpty()) {
            request.header("Authorization", Credentials.basic(u, p));
            return;
        }
        if (config.authToken != null && !config.authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + config.authToken);
            return;
        }
    }

    private HttpUrl urlForSnapshot(final Snapshot snapshot) {
        Instant started = snapshot.started;
        Instant finished = snapshot.ended;
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
        return config.timeseries.newBuilder()
            .addLabels(config.labels)
            .addLabels(Pyroscope.getStaticLabels())
            .build()
            .toString();
    }
}
