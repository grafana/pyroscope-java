package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.config.ProfilingMode;
import io.pyroscope.javaagent.impl.ContinuousProfilingScheduler;
import io.pyroscope.javaagent.impl.PullProfilingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PyroscopeAgentTest {

    private Config configAgentEnabled;
    private Config configAgentDisabled;
    private PyroscopeAgent.Options optionsAgentEnabled;
    private PyroscopeAgent.Options optionsAgentDisabled;

    @Mock
    private Logger logger;

    @Mock
    private ProfilingScheduler profilingScheduler;

    @Mock
    private ProfilerDelegate profiler;

    @BeforeEach
    void setUp() {
        configAgentEnabled = new Config.Builder()
            .setAgentEnabled(true)
            .build();
        optionsAgentEnabled = new PyroscopeAgent.Options.Builder(configAgentEnabled)
            .setScheduler(profilingScheduler)
            .setLogger(logger)
            .setProfiler(profiler)
            .build();

        configAgentDisabled = new Config.Builder()
            .setAgentEnabled(false)
            .build();
        optionsAgentDisabled = new PyroscopeAgent.Options.Builder(configAgentDisabled)
            .setScheduler(profilingScheduler)
            .setLogger(logger)
            .setProfiler(profiler)
            .build();
    }

    @AfterEach
    void tearDown() {
        reset(profilingScheduler);
        PyroscopeAgent.stop();
    }

    @Test
    void startupTestWithEnabledAgent() {
        PyroscopeAgent.start(optionsAgentEnabled);

        verify(profilingScheduler, times(1)).start(any());
        verify(logger, never()).log(eq(Logger.Level.WARN), contains("OTLP export"));
    }

    @Test
    void pullModeSelectsHttpSchedulerWithoutAnExporter() {
        Config config = new Config.Builder().setProfilingMode(ProfilingMode.PULL)
            .setFormat(Format.PPROF).setPullPort(0).build();
        PyroscopeAgent.Options options = new PyroscopeAgent.Options.Builder(config)
            .setLogger(logger).setProfiler(profiler).build();
        assertTrue(options.scheduler instanceof PullProfilingScheduler);
        assertNull(options.exporter);
        assertThrows(IllegalArgumentException.class, () -> new PyroscopeAgent.Options.Builder(config)
            .setProfiler(profiler).setExporter(mock(Exporter.class)).build());
    }

    @Test
    void customSchedulerStopsWithoutAnExporter() {
        PyroscopeAgent.start(optionsAgentEnabled);
        PyroscopeAgent.stop();
        verify(profilingScheduler).stop();
        verify(logger, never()).log(eq(Logger.Level.ERROR), eq("Error stopping profiler %s"), any());
    }

    @Test
    void failedStartupCleansUpComponents() {
        Exporter exporter = mock(Exporter.class);
        doThrow(new IllegalStateException("cannot start")).when(profilingScheduler).start(profiler);
        PyroscopeAgent.Options options = new PyroscopeAgent.Options.Builder(configAgentEnabled)
            .setScheduler(profilingScheduler).setExporter(exporter).setProfiler(profiler).setLogger(logger).build();
        PyroscopeAgent.start(options);
        assertFalse(PyroscopeAgent.isStarted());
        verify(profilingScheduler).stop();
        verify(exporter).stop();
    }

    @Test
    void failedContinuousStartupAllowsRetry() {
        Exporter exporter = mock(Exporter.class);
        ContinuousProfilingScheduler scheduler =
            new ContinuousProfilingScheduler(configAgentEnabled, exporter, logger);
        PyroscopeAgent.Options options = new PyroscopeAgent.Options.Builder(configAgentEnabled)
            .setScheduler(scheduler).setExporter(exporter).setProfiler(profiler).setLogger(logger).build();
        doThrow(new IllegalArgumentException("invalid profiling event")).doNothing().when(profiler).start();

        try {
            PyroscopeAgent.start(options);
            assertFalse(PyroscopeAgent.isStarted());
            verify(exporter).stop();
            verify(profiler, never()).stop();

            PyroscopeAgent.start(options);
            assertTrue(PyroscopeAgent.isStarted());
            verify(profiler, times(2)).start();

            PyroscopeAgent.stop();
            assertFalse(PyroscopeAgent.isStarted());
            verify(profiler).stop();
            verify(exporter, times(2)).stop();
            assertDoesNotThrow(scheduler::stop);
        } finally {
            PyroscopeAgent.stop();
            scheduler.stop();
        }
    }

    @Test
    void schedulerFailureDoesNotSkipExporterCleanup() {
        Exporter exporter = mock(Exporter.class);
        PyroscopeAgent.Options options = new PyroscopeAgent.Options.Builder(configAgentEnabled)
            .setScheduler(profilingScheduler).setExporter(exporter).setProfiler(profiler).setLogger(logger).build();
        PyroscopeAgent.start(options);
        doThrow(new IllegalStateException("cannot stop")).when(profilingScheduler).stop();
        PyroscopeAgent.stop();
        assertTrue(PyroscopeAgent.isStarted());
        verify(exporter).stop();
    }

    @Test
    void failedStopBlocksRestartUntilCleanupSucceeds() {
        PyroscopeAgent.start(optionsAgentEnabled);
        doThrow(new IllegalStateException("worker is still running")).doNothing()
            .when(profilingScheduler).stop();
        PyroscopeAgent.stop();
        assertTrue(PyroscopeAgent.isStarted());

        ProfilingScheduler replacement = mock(ProfilingScheduler.class);
        PyroscopeAgent.Options options = new PyroscopeAgent.Options.Builder(configAgentEnabled)
            .setScheduler(replacement).setProfiler(profiler).setLogger(logger).build();
        PyroscopeAgent.start(options);
        verifyNoInteractions(replacement);

        PyroscopeAgent.stop();
        assertFalse(PyroscopeAgent.isStarted());
        PyroscopeAgent.start(options);
        verify(replacement).start(profiler);
    }

    @Test
    void failedStartupRetainsOwnershipWhenCleanupAlsoFails() {
        doThrow(new IllegalStateException("cannot start")).when(profilingScheduler).start(profiler);
        doThrow(new IllegalStateException("cannot stop")).doNothing().when(profilingScheduler).stop();
        PyroscopeAgent.start(optionsAgentEnabled);
        assertTrue(PyroscopeAgent.isStarted());
        PyroscopeAgent.start(optionsAgentEnabled);
        verify(profilingScheduler).start(profiler);
        PyroscopeAgent.stop();
        assertFalse(PyroscopeAgent.isStarted());
    }

    @Test
    void startupTestWithDisabledAgent() {
        PyroscopeAgent.start(optionsAgentDisabled);

        verify(profilingScheduler, never()).start(any());
    }

    @Test
    void warnsWhenOtlpDoesNotIncludeApplicationNameOrLabels() {
        Config config = new Config.Builder()
            .setAgentEnabled(true)
            .setFormat(Format.OTLP)
            .build();
        PyroscopeAgent.Options options = new PyroscopeAgent.Options.Builder(config)
            .setScheduler(profilingScheduler)
            .setLogger(logger)
            .setProfiler(profiler)
            .build();

        PyroscopeAgent.start(options);

        verify(logger).log(
            Logger.Level.WARN,
            "OTLP export does not include the configured application name or labels; " +
            "profiles may appear under service_name=\"unknown_service\"");
    }
}
