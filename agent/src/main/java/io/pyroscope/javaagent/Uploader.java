package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.config.Config;
import okhttp3.*;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Random;

final class Uploader implements Runnable {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final MediaType PROTOBUF = MediaType.parse("application/x-protobuf");
    private final Logger logger;
    private final OverfillQueue<Snapshot> uploadQueue;
    private final Config config;
    private final OkHttpClient client;

    Uploader(final Logger logger, final OverfillQueue<Snapshot> uploadQueue, final Config config) {
        this.logger = logger;
        this.uploadQueue = uploadQueue;
        this.config = config;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT)
            .readTimeout(TIMEOUT)
            .callTimeout(TIMEOUT)
            .build();
    }

    @Override
    public void run() {
        logger.debug("Uploading started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final Snapshot snapshot = uploadQueue.take();
                uploadSnapshot(snapshot);
            }
        } catch (final InterruptedException e) {
            logger.debug("Uploader interrupted");
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
        HttpUrl.Builder builder = HttpUrl.parse(config.serverAddress)
            .newBuilder()
            .addPathSegment("ingest")
            .addQueryParameter("name", config.timeseriesName)
            .addQueryParameter("units", snapshot.eventType.units.id)
            .addQueryParameter("aggregationType", snapshot.eventType.aggregationType.id)
            .addQueryParameter("sampleRate", Long.toString(config.profilingIntervalInHertz()))
            .addQueryParameter("from", Long.toString(snapshot.started.getEpochSecond()))
            .addQueryParameter("until", Long.toString(snapshot.finished.getEpochSecond()))
            .addQueryParameter("spyName", config.spyName);
        if (config.format == Format.JFR)
            builder.addQueryParameter("format", "jfr");
        return builder.build();
    }
}
