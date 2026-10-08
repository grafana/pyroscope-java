package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.ProfilerDelegate;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.MessageConstraints;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.nio.DefaultHttpServerIODispatch;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.protocol.UriHttpAsyncRequestHandlerMapper;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseConnControl;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns an HTTP listener and records one CPU profile for each accepted scrape. */
public class PullProfilingScheduler implements ProfilingScheduler {
    public static final String PROFILE_PATH = "/debug/pprof/profile";
    private static final int DEFAULT_SECONDS = 10;
    private static final int MAX_SECONDS = 300;
    private static final ThreadFactory THREAD_FACTORY = task -> {
        Thread thread = new Thread(task, "PyroscopePullProfiler");
        thread.setDaemon(true);
        return thread;
    };

    private final Config config;
    private final Logger logger;
    private final AtomicBoolean recording = new AtomicBoolean();
    private volatile boolean running;
    private DefaultListeningIOReactor server;
    private ListenerEndpoint endpoint;
    private ExecutorService listener;
    private ExecutorService executor;

    public PullProfilingScheduler(@NotNull Config config, @NotNull Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    @Override
    public synchronized void start(@NotNull ProfilerDelegate profiler) {
        if (server != null || executor != null) {
            throw new IllegalStateException("already started");
        }
        try {
            server = new DefaultListeningIOReactor(IOReactorConfig.custom()
                .setIoThreadCount(1).setBacklogSize(16).setShutdownGracePeriod(100).build(), THREAD_FACTORY) {
                @Override
                protected void awaitShutdown(long timeout) throws InterruptedException {
                    try {
                        super.awaitShutdown(timeout);
                    } catch (InterruptedException e) {
                        // shutdown() catches this exception; keep the interrupt visible to our caller.
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            };
            executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), THREAD_FACTORY);
            UriHttpAsyncRequestHandlerMapper handlers = new UriHttpAsyncRequestHandlerMapper();
            handlers.register("*", new HttpAsyncRequestHandler<HttpRequest>() {
                @Override
                public HttpAsyncRequestConsumer<HttpRequest> processRequest(HttpRequest request,
                    HttpContext context) throws HttpException {
                    // Scrapes have no request body; do not buffer arbitrary client payloads.
                    if (request instanceof HttpEntityEnclosingRequest &&
                        ((HttpEntityEnclosingRequest) request).getEntity().getContentLength() != 0) {
                        throw new ProtocolException("Request body is not supported");
                    }
                    return new BasicAsyncRequestConsumer();
                }

                @Override
                public void handle(HttpRequest request, HttpAsyncExchange exchange, HttpContext context) {
                    PullProfilingScheduler.this.handle(exchange, request, profiler);
                }
            });
            HttpAsyncService service = new HttpAsyncService(HttpProcessorBuilder.create()
                .add(new ResponseContent()).add(new ResponseConnControl()).build(),
                (response, context) -> false, null, handlers, null);
            ConnectionConfig connectionConfig = ConnectionConfig.custom().setMessageConstraints(
                MessageConstraints.custom().setMaxLineLength(8192).setMaxHeaderCount(100).build()).build();
            DefaultHttpServerIODispatch<HttpAsyncService> dispatch =
                new DefaultHttpServerIODispatch<>(service, connectionConfig);
            endpoint = server.listen(new InetSocketAddress(config.pullBindAddress, config.pullPort));
            listener = Executors.newSingleThreadExecutor(THREAD_FACTORY);
            running = true;
            listener.execute(() -> {
                try {
                    server.execute(dispatch);
                } catch (IOException e) {
                    logger.log(Logger.Level.ERROR, "Pull profiler listener failed: %s", e);
                }
            });
            endpoint.waitFor();
            if (endpoint.getException() != null) {
                throw endpoint.getException();
            }
            logger.log(Logger.Level.INFO, "Pull profiler listening on %s", endpoint.getAddress());
        } catch (Exception e) {
            try {
                stop();
            } catch (Exception cleanupError) {
                e.addSuppressed(cleanupError);
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to start pull profiler", e);
        }
    }

    /** Returns the bound address, including the assigned port when configured with port zero. */
    public synchronized InetSocketAddress getAddress() {
        if (!running) {
            throw new IllegalStateException("not started");
        }
        return (InetSocketAddress) endpoint.getAddress();
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (endpoint != null) {
            endpoint.close();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (listener != null) {
            listener.shutdown();
        }
        try {
            if (server != null) {
                server.shutdown(1);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stop pull profiler listener", e);
        }
        try {
            if (executor != null && !executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to terminate pull profiler executor");
            }
            if (listener != null && !listener.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to terminate pull profiler listener");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted stopping pull profiler", e);
        }
        // Retain these references on failure so stop can be retried and start stays blocked.
        executor = null;
        listener = null;
        server = null;
        endpoint = null;
        recording.set(false);
    }

    private void handle(HttpAsyncExchange exchange, HttpRequest request, ProfilerDelegate profiler) {
        HttpUrl url = HttpUrl.parse("http://localhost" + request.getRequestLine().getUri());
        if (url == null || !PROFILE_PATH.equals(url.encodedPath())) {
            error(exchange, 404, "Not found");
            return;
        }
        if (!"GET".equals(request.getRequestLine().getMethod())) {
            error(exchange, 405, "Method not allowed");
            return;
        }
        int seconds;
        try {
            seconds = durationSeconds(url);
        } catch (IllegalArgumentException e) {
            error(exchange, 400, "seconds must be an integer between 1 and " + MAX_SECONDS);
            return;
        }
        if (!running) {
            error(exchange, 503, "Profiler is stopping");
            return;
        }
        if (!recording.compareAndSet(false, true)) {
            error(exchange, 429, "A profile is already being collected");
            return;
        }
        Recording task = new Recording(exchange, profiler, seconds);
        exchange.setCallback(() -> { task.cancel(); return true; });
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            recording.set(false);
            error(exchange, 503, "Profiler is stopping");
        }
    }

    private final class Recording implements Runnable {
        private final HttpAsyncExchange exchange;
        private final ProfilerDelegate profiler;
        private final int seconds;
        private Thread worker;
        private volatile boolean cancelled;

        Recording(HttpAsyncExchange exchange, ProfilerDelegate profiler, int seconds) {
            this.exchange = exchange;
            this.profiler = profiler;
            this.seconds = seconds;
        }

        synchronized void cancel() {
            cancelled = true;
            if (worker != null) {
                worker.interrupt();
            }
        }

        @Override
        public void run() {
            Snapshot snapshot = null;
            try {
                synchronized (this) {
                    if (cancelled || !running) {
                        return;
                    }
                    worker = Thread.currentThread();
                }
                profiler.setConfig(config.newBuilder().setUploadInterval(Duration.ofSeconds(seconds)).build());
                Instant started = Instant.now();
                profiler.start();
                try {
                    TimeUnit.SECONDS.sleep(seconds);
                } finally {
                    profiler.stop();
                }
                if (!running || Thread.currentThread().isInterrupted() || cancelled) {
                    return;
                }
                snapshot = profiler.dumpProfile(started, Instant.now());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.log(Logger.Level.ERROR, "Error collecting pull profile: %s", e);
            } finally {
                synchronized (this) {
                    worker = null;
                }
                // Cancellation requests do not release ownership until profiler cleanup completes.
                recording.set(false);
            }
            if (snapshot != null) {
                respond(exchange, 200, snapshot.data);
            } else {
                error(exchange, 500, "Failed to collect profile");
            }
        }
    }

    private static int durationSeconds(HttpUrl url) {
        List<String> values = url.queryParameterValues("seconds");
        if (values.isEmpty()) {
            return DEFAULT_SECONDS;
        }
        if (values.size() != 1 || values.get(0) == null || !values.get(0).matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid duration");
        }
        int seconds = Integer.parseInt(values.get(0));
        if (seconds < 1 || seconds > MAX_SECONDS) {
            throw new IllegalArgumentException("Invalid duration");
        }
        return seconds;
    }

    private void error(HttpAsyncExchange exchange, int status, String message) {
        respond(exchange, status, message.getBytes(StandardCharsets.UTF_8));
    }

    private void respond(HttpAsyncExchange exchange, int status, byte[] data) {
        HttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.setHeader("Content-Type",
            status == 200 ? "application/octet-stream" : "text/plain; charset=utf-8");
        if (status == 200) {
            response.setHeader("Content-Disposition", "attachment; filename=profile.pb.gz");
        } else if (status == 405) {
            response.setHeader("Allow", "GET");
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Connection", "close");
        response.setEntity(new ByteArrayEntity(data));
        exchange.submitResponse();
    }
}
