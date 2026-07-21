package io.pyroscope.javaagent.impl;

import com.sun.net.httpserver.HttpServer;
import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.v2.Pyroscope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    void exportsOtlpAsRawProtobufToProfilesEndpoint() throws Exception {
        byte[] profile = new byte[] {1, 2, 3, 4};
        String[] contentType = new String[1];
        byte[][] requestBody = new byte[1][];
        CountDownLatch requestCaptured = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1development/profiles", exchange -> {
            contentType[0] = exchange.getRequestHeaders().getFirst("Content-Type");
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
            assertEquals(Arrays.toString(profile), Arrays.toString(requestBody[0]));
        } finally {
            exporter.stop();
            server.stop(0);
        }
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
