package io.pyroscope.javaagent.impl;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.ProfilerDelegate;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.config.ProfilingMode;
import io.pyroscope.labels.pb.JfrLabels;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PullProfilingSchedulerTest {
    private final Config config = new Config.Builder().setProfilingMode(ProfilingMode.PULL)
        .setFormat(Format.PPROF).setPullPort(0).setUploadInterval(Duration.ofSeconds(60)).build();
    private final ProfilerDelegate profiler = mock(ProfilerDelegate.class);
    private final Logger logger = mock(Logger.class);
    private final PullProfilingScheduler scheduler = new PullProfilingScheduler(config, logger);
    private final OkHttpClient client = new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void start() {
        scheduler.start(profiler);
    }

    @AfterEach
    void stop() {
        scheduler.stop();
        client.dispatcher().executorService().shutdownNow();
        client.connectionPool().evictAll();
    }

    @Test
    void servesFreshProfilesAndUsesTheRequestedRecordingDuration() throws Exception {
        byte[] body = PprofEncoder.encode("root;leaf 2\n", Instant.EPOCH, Instant.EPOCH.plusSeconds(1),
            10_000_000, Collections.emptyMap());
        when(profiler.dumpProfile(any(), any())).thenAnswer(invocation ->
            new Snapshot(Format.PPROF, EventType.ITIMER, invocation.getArgument(0),
                invocation.getArgument(1), body, JfrLabels.LabelsSnapshot.getDefaultInstance()));
        for (int i = 0; i < 2; i++) {
            try (Response response = get("/debug/pprof/profile?seconds=1")) {
                assertEquals(200, response.code());
                assertEquals("application/octet-stream", response.header("Content-Type"));
                assertEquals("no-store", response.header("Cache-Control"));
                assertArrayEquals(body, response.body().bytes());
            }
        }
        InOrder order = inOrder(profiler);
        ArgumentCaptor<Config> recordingConfig = ArgumentCaptor.forClass(Config.class);
        ArgumentCaptor<Instant> started = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> ended = ArgumentCaptor.forClass(Instant.class);
        for (int i = 0; i < 2; i++) {
            order.verify(profiler).setConfig(recordingConfig.capture());
            order.verify(profiler).start();
            order.verify(profiler).stop();
            order.verify(profiler).dumpProfile(started.capture(), ended.capture());
        }
        order.verifyNoMoreInteractions();
        assertEquals(Duration.ofSeconds(1), recordingConfig.getValue().uploadInterval);
        assertEquals(Duration.ofSeconds(60), config.uploadInterval);
        assertFalse(started.getAllValues().get(1).isBefore(ended.getAllValues().get(0)));
    }

    @Test
    void rejectsInvalidRequestsWithoutTouchingTheProfiler() throws Exception {
        for (String value : new String[]{"0", "-1", "301", "1.5", "NaN", "", "999999999999", "1&seconds=2"}) {
            try (Response response = get("/debug/pprof/profile?seconds=" + value)) {
                assertEquals(400, response.code(), value);
            }
        }
        try (Response response = get("/debug/pprof/profile/extra")) {
            assertEquals(404, response.code());
        }
        try (Response response = client.newCall(new Request.Builder().url(url("/debug/pprof/profile"))
            .post(RequestBody.create(new byte[0])).build()).execute()) {
            assertEquals(405, response.code());
            assertEquals("GET", response.header("Allow"));
        }
        verifyNoInteractions(profiler);
    }

    @Test
    void rejectsOverlappingScrapesAndCancelsRecordingOnShutdown() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        doAnswer(invocation -> { started.countDown(); return null; }).when(profiler).start();
        ExecutorService requests = Executors.newSingleThreadExecutor();
        try {
            Future<?> request = requests.submit(() -> {
                try (Response ignored = get("/debug/pprof/profile?seconds=300")) {
                    fail("Shutdown must close an unfinished scrape");
                } catch (java.io.IOException expected) {
                    // The listener closes the exchange when shutdown interrupts recording.
                }
            });
            assertTrue(started.await(3, TimeUnit.SECONDS));
            try (Response response = get("/debug/pprof/profile?seconds=1")) {
                assertEquals(429, response.code());
            }
            scheduler.stop();
            request.get(3, TimeUnit.SECONDS);
            verify(profiler).start();
            verify(profiler).stop();
            verify(profiler, never()).dumpProfile(any(), any());
        } finally {
            requests.shutdownNow();
        }
    }

    @Test
    void disconnectStopsRecordingAndAllowsAnotherScrape() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        doAnswer(invocation -> { started.countDown(); return null; }).when(profiler).start();
        doAnswer(invocation -> { stopped.countDown(); return null; }).when(profiler).stop();
        byte[] body = PprofEncoder.encode("root;leaf 1\n", Instant.EPOCH, Instant.EPOCH.plusSeconds(1),
            10_000_000, Collections.emptyMap());
        when(profiler.dumpProfile(any(), any())).thenAnswer(invocation ->
            new Snapshot(Format.PPROF, EventType.ITIMER, invocation.getArgument(0),
                invocation.getArgument(1), body, JfrLabels.LabelsSnapshot.getDefaultInstance()));

        try (Socket socket = scrapeSocket(300)) {
            assertTrue(started.await(3, TimeUnit.SECONDS));
        }
        assertTrue(stopped.await(3, TimeUnit.SECONDS), "Disconnect should stop a 300-second recording promptly");
        verify(profiler, never()).dumpProfile(any(), any());

        // stop() may have signalled the latch just before the worker releases recording ownership.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (true) {
            try (Response response = get("/debug/pprof/profile?seconds=1")) {
                if (response.code() == 200) {
                    assertArrayEquals(body, response.body().bytes());
                    break;
                }
                assertEquals(429, response.code());
            }
            assertTrue(System.nanoTime() < deadline, "Cancelled scrape retained recording ownership");
            Thread.sleep(10);
        }
        verify(profiler, times(2)).start();
        verify(profiler, times(2)).stop();
        verify(profiler).dumpProfile(any(), any());
    }

    @Test
    void interruptedShutdownPreventsRestartUntilWorkerExits() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> { started.countDown(); return null; }).when(profiler).start();
        doAnswer(invocation -> { awaitUninterruptibly(release); return null; }).when(profiler).stop();
        try (Socket socket = scrapeSocket(300)) {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            Thread.currentThread().interrupt();
            try {
                assertThrows(IllegalStateException.class, scheduler::stop);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertThrows(IllegalStateException.class, () -> scheduler.start(profiler));
        } finally {
            release.countDown();
            scheduler.stop();
        }
        scheduler.start(profiler);
        assertTrue(scheduler.getAddress().getPort() > 0);
        verify(profiler).start();
        verify(profiler, never()).dumpProfile(any(), any());
    }

    @Test
    void shutdownTimeoutPreventsRestartUntilDumpExits() throws Exception {
        CountDownLatch dumping = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(profiler.dumpProfile(any(), any())).thenAnswer(invocation -> {
            dumping.countDown();
            awaitUninterruptibly(release);
            return new Snapshot(Format.PPROF, EventType.ITIMER, invocation.getArgument(0),
                invocation.getArgument(1), new byte[0], JfrLabels.LabelsSnapshot.getDefaultInstance());
        });
        try (Socket socket = scrapeSocket(1)) {
            assertTrue(dumping.await(3, TimeUnit.SECONDS));
            IllegalStateException failure = assertThrows(IllegalStateException.class, scheduler::stop);
            assertEquals("Failed to terminate pull profiler executor", failure.getMessage());
            assertThrows(IllegalStateException.class, () -> scheduler.start(profiler));
        } finally {
            release.countDown();
            scheduler.stop();
        }
        scheduler.start(profiler);
        assertTrue(scheduler.getAddress().getPort() > 0);
        verify(profiler).start();
    }

    private Socket scrapeSocket(int seconds) throws java.io.IOException {
        Socket socket = new Socket();
        socket.connect(scheduler.getAddress(), 3000);
        socket.getOutputStream().write(("GET /debug/pprof/profile?seconds=" + seconds +
            " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        return socket;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void releasesRecordingOwnershipAfterAnError() throws Exception {
        doThrow(new IllegalStateException("cannot start")).when(profiler).start();
        for (int i = 0; i < 2; i++) {
            try (Response response = get("/debug/pprof/profile?seconds=1")) {
                assertEquals(500, response.code());
            }
        }
        verify(profiler, times(2)).start();
        verify(profiler, never()).stop();
    }

    @Test
    void stopsIdempotentlyAndReleasesThePort() throws Exception {
        InetSocketAddress address = scheduler.getAddress();
        scheduler.stop();
        scheduler.stop();
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(address);
        }
        scheduler.start(profiler);
        assertTrue(scheduler.getAddress().getPort() > 0);
        verifyNoInteractions(profiler);
    }

    @Test
    void bindFailureDoesNotStartTheProfilerAndCanBeRetried() throws Exception {
        InetSocketAddress address = scheduler.getAddress();
        PullProfilingScheduler other = new PullProfilingScheduler(
            config.newBuilder().setPullPort(address.getPort()).build(), logger);
        try {
            assertThrows(IllegalStateException.class, () -> other.start(profiler));
            other.stop();
            scheduler.stop();
            other.start(profiler);
            assertEquals(address, other.getAddress());
        } finally {
            other.stop();
        }
        verifyNoInteractions(profiler);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + scheduler.getAddress().getPort() + path;
    }

    private Response get(String path) throws java.io.IOException {
        return client.newCall(new Request.Builder().url(url(path)).build()).execute();
    }
}
