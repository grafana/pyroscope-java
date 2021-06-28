package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;
import org.apache.logging.log4j.Logger;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

final class Uploader implements Runnable {
    private final Logger logger;
    private final OverfillQueue<Snapshot> uploadQueue;
    private final Config config;

    Uploader(final Logger logger, final OverfillQueue<Snapshot> uploadQueue, final Config config) {
        this.logger = logger;
        this.uploadQueue = uploadQueue;
        this.config = config;
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
        }
    }

    private void uploadSnapshot(final Snapshot snapshot) throws InterruptedException {
        logger.debug("Uploading {}", snapshot);
        final URL url = urlForSnapshot(snapshot);

        final ExponentialBackoff exponentialBackoff = new ExponentialBackoff(1_000, 30_000, new Random());
        boolean success = false;
        while (!success) {
            logger.debug("Upload attempt");
            try {
                final HttpURLConnection conn = prepareConnection(url);
                sendRequest(conn, snapshot);

                int status = conn.getResponseCode();
                final String responseBody = InputStreamUtils.readToString(conn.getInputStream());
                if (status >= 400) {
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

    private URL urlForSnapshot(final Snapshot snapshot) {
        try {
            final URI baseUri = URI.create(config.serverAddress);
            final String query = urlParam("name", config.applicationName)
                    + "&" + urlParam("from", Long.toString(snapshot.started.getEpochSecond()))
                    + "&" + urlParam("until", Long.toString(snapshot.finished.getEpochSecond()))
                    + "&" + urlParam("spyName", config.spyName);
            return new URI(baseUri.getScheme(), baseUri.getUserInfo(), baseUri.getHost(), baseUri.getPort(),
                    baseUri.getPath() + "/ingest", query, null).toURL();
        } catch (final MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static String urlParam(final String name, final String value) {
        try {
            return URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpURLConnection prepareConnection(final URL url) throws IOException {
        // The internal connection pool is used.
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("Connection", "keep-alive");

        if (config.authToken != null && !config.authToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + config.authToken);
        }

        return conn;
    }

    private static void sendRequest(HttpURLConnection conn, final Snapshot snapshot) throws IOException {
        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            out.write(snapshot.data.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
