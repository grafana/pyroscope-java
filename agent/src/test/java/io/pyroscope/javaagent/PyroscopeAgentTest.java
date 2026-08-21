package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        PyroscopeAgent.stop();
    }

    @Test
    void startupTestWithEnabledAgent() {
        PyroscopeAgent.start(optionsAgentEnabled);

        verify(profilingScheduler, times(1)).start(any());
        verify(logger, never()).log(eq(Logger.Level.WARN), contains("OTLP export"));
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
