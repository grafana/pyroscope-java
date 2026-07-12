package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Random;

/** Exports the experimental OpenTelemetry Profiles protobuf over OTLP/HTTP. */
public final class OtlpHttpExporter implements Exporter {
    private static final MediaType PROTOBUF = MediaType.parse("application/x-protobuf");
    private static final String PROFILES_PATH = "v1development/profiles";

    private final Config config;
    private final Logger logger;
    private final OkHttpClient client;
    private final HttpUrl endpoint;

    public OtlpHttpExporter(Config config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(config.profileExportTimeout)
            .readTimeout(config.profileExportTimeout)
            .callTimeout(config.profileExportTimeout)
            .build();
        this.endpoint = HttpUrl.parse(config.serverAddress).newBuilder()
            .addPathSegments(PROFILES_PATH)
            .build();
    }

    @Override
    public void export(@NotNull Snapshot snapshot) {
        try {
            upload(snapshot);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void upload(Snapshot snapshot) throws InterruptedException {
        ExponentialBackoff backoff = new ExponentialBackoff(1_000, 30_000, new Random());
        boolean retry = true;
        int tries = 0;
        while (retry) {
            tries++;
            logger.log(Logger.Level.DEBUG, "OTLP upload attempt %d to %s. Profile: %s bytes", tries, endpoint, snapshot.data.length);
            Request.Builder request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(snapshot.data, PROTOBUF));
            config.httpHeaders.forEach(request::header);
            addAuthHeader(request);

            try (Response response = client.newCall(request.build()).execute()) {
                int status = response.code();
                retry = status == 429 || status >= 500 && status <= 599;
                if (status >= 400) {
                    ResponseBody body = response.body();
                    logger.log(Logger.Level.ERROR, "Error uploading OTLP profile: %s %s", status, body == null ? "" : body.string());
                }
            } catch (IOException e) {
                logger.log(Logger.Level.ERROR, "Error uploading OTLP profile: %s", e.getMessage());
            }

            if (retry) {
                if (config.ingestMaxTries >= 0 && tries >= config.ingestMaxTries) {
                    logger.log(Logger.Level.ERROR, "Gave up uploading OTLP profile after %d tries", tries);
                    break;
                }
                int delay = backoff.error();
                logger.log(Logger.Level.DEBUG, "Backing off for %s ms", delay);
                Thread.sleep(delay);
            }
        }
    }

    private void addAuthHeader(Request.Builder request) {
        if (config.tenantID != null && !config.tenantID.isEmpty()) {
            request.header("X-Scope-OrgID", config.tenantID);
        }
        if (config.basicAuthUser != null && !config.basicAuthUser.isEmpty()
            && config.basicAuthPassword != null && !config.basicAuthPassword.isEmpty()) {
            request.header("Authorization", Credentials.basic(config.basicAuthUser, config.basicAuthPassword));
        } else if (!endpoint.username().isEmpty() && !endpoint.password().isEmpty()) {
            request.header("Authorization", Credentials.basic(endpoint.username(), endpoint.password()));
        } else if (config.authToken != null && !config.authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + config.authToken);
        }
    }

    @Override
    public void stop() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        try {
            if (client.cache() != null) client.cache().close();
        } catch (IOException ignored) {
        }
    }
}
