package io.pyroscope.javaagent.impl;

import com.sun.net.httpserver.HttpServer;
import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.pb.JfrLabels;
import io.pyroscope.labels.v2.Pyroscope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * The labels part is sent straight out of the encoder's backing buffer, which may be longer
     * than the encoded message, so the exporter has to send exactly the [0, size()) prefix.
     */
    @Test
    void exportsOnlyTheEncodedPrefixOfTheLabelsSnapshot() throws Exception {
        // An encoded LabelsSnapshot with one string table entry: 1 -> "test".
        byte[] encoded = {0x12, 0x08, 0x08, 0x01, 0x12, 0x04, 't', 'e', 's', 't'};
        // A buffer with slack after the message, as the encoder hands it over.
        byte[] withSlack = Arrays.copyOf(encoded, encoded.length + 64);
        JfrLabels.LabelsSnapshot labels = new JfrLabels.LabelsSnapshot(withSlack, encoded.length);

        byte[] jfr = new byte[]{9, 8, 7};
        Map<String, byte[]> parts = new HashMap<>();
        CountDownLatch requestCaptured = new CountDownLatch(1);
        String[] contentType = new String[1];
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ingest", exchange -> {
            contentType[0] = exchange.getRequestHeaders().getFirst("Content-Type");
            parts.putAll(parseMultipart(contentType[0], readAllBytes(exchange.getRequestBody())));
            requestCaptured.countDown();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        PyroscopeExporter exporter = new PyroscopeExporter(
            new Config.Builder()
                .setApplicationName("test.app")
                .setFormat(Format.JFR)
                .setServerAddress("http://localhost:" + server.getAddress().getPort())
                .setCompressionLevelJFR(Deflater.NO_COMPRESSION)
                .setCompressionLevelLabels(Deflater.NO_COMPRESSION)
                .build(),
            NOOP_LOGGER);
        try {
            exporter.export(new Snapshot(
                Format.JFR, EventType.CPU, Instant.EPOCH, Instant.EPOCH, jfr, labels));
            assertTrue(requestCaptured.await(5, TimeUnit.SECONDS));
            assertArrayEquals(jfr, parts.get("jfr"));
            assertArrayEquals(encoded, parts.get("labels"));
        } finally {
            exporter.stop();
            server.stop(0);
        }
    }

    /** A null labels snapshot must not be sent, and must not blow up. */
    @Test
    void exportsWithoutALabelsPartWhenThereAreNoLabels() throws Exception {
        byte[] jfr = new byte[]{1};
        Map<String, byte[]> parts = new HashMap<>();
        CountDownLatch requestCaptured = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ingest", exchange -> {
            parts.putAll(parseMultipart(
                exchange.getRequestHeaders().getFirst("Content-Type"),
                readAllBytes(exchange.getRequestBody())));
            requestCaptured.countDown();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        PyroscopeExporter exporter = new PyroscopeExporter(
            new Config.Builder()
                .setApplicationName("test.app")
                .setFormat(Format.JFR)
                .setServerAddress("http://localhost:" + server.getAddress().getPort())
                .setCompressionLevelJFR(Deflater.NO_COMPRESSION)
                .setCompressionLevelLabels(Deflater.NO_COMPRESSION)
                .build(),
            NOOP_LOGGER);
        try {
            exporter.export(new Snapshot(
                Format.JFR, EventType.CPU, Instant.EPOCH, Instant.EPOCH, jfr, null));
            assertTrue(requestCaptured.await(5, TimeUnit.SECONDS));
            assertArrayEquals(jfr, parts.get("jfr"));
            assertFalse(parts.containsKey("labels"), "labels part should be omitted");

            exporter.export(new Snapshot(Format.JFR, EventType.CPU, Instant.EPOCH, Instant.EPOCH,
                jfr, JfrLabels.LabelsSnapshot.EMPTY));
        } finally {
            exporter.stop();
            server.stop(0);
        }
    }

    /** Minimal multipart/form-data splitter: form field name to raw body bytes. */
    private static Map<String, byte[]> parseMultipart(String contentType, byte[] body) {
        int boundaryAt = contentType.indexOf("boundary=");
        byte[] delimiter = ("--" + contentType.substring(boundaryAt + "boundary=".length()))
            .getBytes(StandardCharsets.ISO_8859_1);
        Map<String, byte[]> parts = new HashMap<>();
        int from = indexOf(body, delimiter, 0);
        while (from >= 0) {
            int start = from + delimiter.length;
            int next = indexOf(body, delimiter, start);
            if (next < 0) {
                break;
            }
            // Skip the CRLF after the boundary, then split headers from the body on a blank line.
            byte[] section = Arrays.copyOfRange(body, start + 2, next - 2);
            byte[] blankLine = new byte[]{'\r', '\n', '\r', '\n'};
            int headerEnd = indexOf(section, blankLine, 0);
            String headers = new String(section, 0, headerEnd, StandardCharsets.ISO_8859_1);
            int nameAt = headers.indexOf("name=\"");
            String name = headers.substring(nameAt + 6, headers.indexOf('"', nameAt + 6));
            parts.put(name, Arrays.copyOfRange(section, headerEnd + 4, section.length));
            from = next;
        }
        return parts;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i + needle.length <= haystack.length; i++) {
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
