package io.pyroscope.javaagent.impl;

import com.sun.net.httpserver.HttpServer;
import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.SnapshotFile;
import io.pyroscope.javaagent.TestSnapshotFiles;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.v2.Pyroscope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PyroscopeExporterTest {
    private static final Logger NOOP_LOGGER = (level, msg, args) -> {};

    @TempDir
    Path tempDir;

    @BeforeEach
    @AfterEach
    void resetStaticLabels() {
        Pyroscope.setStaticLabels(Collections.emptyMap());
    }

    @Test
    void addsRequiredOtelLabels() {
        PyroscopeExporter exporter = new PyroscopeExporter(
            new Config.Builder()
                .setApplicationName("test.app")
                .build(),
            NOOP_LOGGER);
        try {
            Map<String, String> labels = labelsFromSeriesName(exporter.staticLabels);

            assertEquals("com.grafana.pyroscope/java", labels.get("otel.scope.name"));
            if (labels.containsKey("otel.scope.version")) {
                assertFalse(labels.get("otel.scope.version").isEmpty());
            }
            assertEquals(System.getProperty("java.runtime.name"), labels.get("process.runtime.name"));
            assertEquals(System.getProperty("java.runtime.version"), labels.get("process.runtime.version"));
        } finally {
            exporter.stop();
        }
    }

    @Test
    void staticLabelsOverrideRequiredOtelDefaults() {
        Pyroscope.setStaticLabels(mapOf(
            "otel.scope.name", "staticScopeName",
            "otel.scope.version", "staticScopeVersion",
            "process.runtime.name", "staticRuntimeName",
            "process.runtime.version", "staticRuntimeVersion"));

        PyroscopeExporter exporter = new PyroscopeExporter(
            new Config.Builder()
                .setApplicationName("test.app")
                .build(),
            NOOP_LOGGER);
        try {
            Map<String, String> labels = labelsFromSeriesName(exporter.staticLabels);

            assertEquals("staticScopeName", labels.get("otel.scope.name"));
            assertEquals("staticScopeVersion", labels.get("otel.scope.version"));
            assertEquals("staticRuntimeName", labels.get("process.runtime.name"));
            assertEquals("staticRuntimeVersion", labels.get("process.runtime.version"));
        } finally {
            exporter.stop();
        }
    }

    @Test
    void userLabelsOverrideRequiredOtelDefaults() {
        Map<String, String> configLabels = mapOf(
            "process.runtime.name", "configRuntimeName",
            "process.runtime.version", "configRuntimeVersion");
        Map<String, String> staticLabels = mapOf(
            "otel.scope.version", "staticScopeVersion");
        Pyroscope.setStaticLabels(staticLabels);

        PyroscopeExporter exporter = new PyroscopeExporter(
            new Config.Builder()
                .setApplicationName("test.app{otel.scope.name=appScopeName}")
                .setLabels(configLabels)
                .build(),
            NOOP_LOGGER);
        try {
            Map<String, String> labels = labelsFromSeriesName(exporter.staticLabels);

            assertEquals("appScopeName", labels.get("otel.scope.name"));
            assertEquals("staticScopeVersion", labels.get("otel.scope.version"));
            assertEquals("configRuntimeName", labels.get("process.runtime.name"));
            assertEquals("configRuntimeVersion", labels.get("process.runtime.version"));
        } finally {
            exporter.stop();
        }
    }

    @Test
    void exportsOtlpAsGzippedProtobufToProfilesEndpoint() throws Exception {
        byte[] profile = new byte[] {1, 2, 3, 4};
        String[] contentType = new String[1];
        String[] contentEncoding = new String[1];
        byte[][] requestBody = new byte[1][];
        CountDownLatch requestCaptured = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1development/profiles", exchange -> {
            contentType[0] = exchange.getRequestHeaders().getFirst("Content-Type");
            contentEncoding[0] = exchange.getRequestHeaders().getFirst("Content-Encoding");
            requestBody[0] = readAllBytes(exchange.getRequestBody());
            requestCaptured.countDown();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        PyroscopeExporter exporter = new PyroscopeExporter(
            new Config.Builder()
                .setFormat(Format.OTLP)
                .setServerAddress("http://localhost:" + server.getAddress().getPort())
                .build(),
            NOOP_LOGGER);
        try {
            exporter.export(new Snapshot(
                Format.OTLP, EventType.CPU, Instant.EPOCH, Instant.EPOCH, profile, null));
            assertTrue(requestCaptured.await(5, TimeUnit.SECONDS));
            assertEquals("application/x-protobuf", contentType[0]);
            assertEquals("gzip", contentEncoding[0]);
            assertArrayEquals(profile, readAllBytes(new GZIPInputStream(
                new ByteArrayInputStream(requestBody[0]))));
        } finally {
            exporter.stop();
            server.stop(0);
        }
    }

    @Test
    void streamsSnapshotFileAsGzippedJfrPartAndDeletesIt() throws Exception {
        byte[] profile = randomProfile();
        SnapshotFile snapshot = new TestSnapshotFiles(tempDir, 2).take(profile);
        List<byte[]> requests = new ArrayList<>();
        HttpServer server = ingestServer(requests, 200);

        PyroscopeExporter exporter = new PyroscopeExporter(
            new Config.Builder()
                .setServerAddress("http://localhost:" + server.getAddress().getPort())
                .build(),
            NOOP_LOGGER);
        try {
            exporter.export(snapshot);
            List<byte[]> received = received(requests);
            assertEquals(1, received.size());
            assertArrayEquals(profile, gunzipPart(received.get(0)));
            assertFalse(Files.exists(snapshot.file()), "uploaded snapshot file should be deleted");
        } finally {
            exporter.stop();
            server.stop(0);
        }
    }

    @Test
    void rereadsSnapshotFileOnRetry() throws Exception {
        byte[] profile = randomProfile();
        SnapshotFile snapshot = new TestSnapshotFiles(tempDir, 2).take(profile);
        List<byte[]> requests = new ArrayList<>();
        HttpServer server = ingestServer(requests, 503, 200);

        PyroscopeExporter exporter = new PyroscopeExporter(
            new Config.Builder()
                .setServerAddress("http://localhost:" + server.getAddress().getPort())
                .setCompressionLevelJFR(Deflater.NO_COMPRESSION)
                .build(),
            NOOP_LOGGER);
        try {
            exporter.export(snapshot);
            List<byte[]> received = received(requests);
            assertEquals(2, received.size());
            for (byte[] request : received) {
                assertTrue(indexOf(request, profile) >= 0, "every attempt should contain the whole profile");
            }
            assertFalse(Files.exists(snapshot.file()));
        } finally {
            exporter.stop();
            server.stop(0);
        }
    }

    @Test
    void dropsSupersededSnapshotFile() throws Exception {
        TestSnapshotFiles files = new TestSnapshotFiles(tempDir, 1);
        SnapshotFile superseded = files.take(new byte[] {1});
        SnapshotFile current = files.take(new byte[] {2});
        List<byte[]> requests = new ArrayList<>();
        HttpServer server = ingestServer(requests, 200);

        PyroscopeExporter exporter = new PyroscopeExporter(
            new Config.Builder()
                .setServerAddress("http://localhost:" + server.getAddress().getPort())
                .build(),
            NOOP_LOGGER);
        try {
            exporter.export(superseded);
            assertEquals(0, received(requests).size());
            assertTrue(Files.exists(current.file()), "newer snapshot file must be kept");
        } finally {
            exporter.stop();
            server.stop(0);
        }
    }

    /**
     * Records request bodies of /ingest calls and answers with the given statuses, the last one repeating.
     */
    private static HttpServer ingestServer(List<byte[]> requests, int... statuses) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ingest", exchange -> {
            byte[] body = readAllBytes(exchange.getRequestBody());
            int status;
            synchronized (requests) {
                requests.add(body);
                status = statuses[Math.min(requests.size(), statuses.length) - 1];
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    /**
     * Copies the recorded requests under the lock the handler thread writes them with. export() returns only after
     * the server responded, and the handler records the body before responding.
     */
    private static List<byte[]> received(List<byte[]> requests) {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    private static byte[] randomProfile() {
        // larger than several Okio segments and file transfer chunks
        byte[] profile = new byte[100_000];
        new Random(42).nextBytes(profile);
        return profile;
    }

    private static byte[] gunzipPart(byte[] multipartBody) throws IOException {
        int start = indexOf(multipartBody, new byte[] {0x1f, (byte) 0x8b, 8});
        assertTrue(start >= 0, "no gzip stream in request");
        // GZIPInputStream stops at the gzip trailer and ignores the multipart bytes after it
        return readAllBytes(new GZIPInputStream(
            new ByteArrayInputStream(multipartBody, start, multipartBody.length - start)));
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static Map<String, String> labelsFromSeriesName(String seriesName) {
        int labelsStart = seriesName.indexOf('{');
        int labelsEnd = seriesName.lastIndexOf('}');
        if (labelsStart == -1 || labelsEnd == -1 || labelsStart >= labelsEnd) {
            return Collections.emptyMap();
        }

        Map<String, String> labels = new HashMap<>();
        String labelsString = seriesName.substring(labelsStart + 1, labelsEnd);
        for (String label : labelsString.split(",")) {
            int separator = label.indexOf('=');
            if (separator == -1) {
                continue;
            }
            labels.put(label.substring(0, separator), label.substring(separator + 1));
        }
        return labels;
    }

    private static Map<String, String> mapOf(String... pairs) {
        Map<String, String> labels = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            labels.put(pairs[i], pairs[i + 1]);
        }
        return labels;
    }
}
