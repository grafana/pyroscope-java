package io.pyroscope.javaagent;

import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
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

    @BeforeEach
    void setUp() {
        configAgentEnabled = new Config.Builder()
            .setAgentEnabled(true)
            .build();
        optionsAgentEnabled = new PyroscopeAgent.Options.Builder(configAgentEnabled)
            .setScheduler(profilingScheduler)
            .setLogger(logger)
            .build();

        configAgentDisabled = new Config.Builder()
            .setAgentEnabled(false)
            .build();
        optionsAgentDisabled = new PyroscopeAgent.Options.Builder(configAgentDisabled)
            .setScheduler(profilingScheduler)
            .setLogger(logger)
            .build();
    }

    @Test
    void startupTestWithEnabledAgent() {
        PyroscopeAgent.start(optionsAgentEnabled);

        verify(profilingScheduler, times(1)).start(any());

        PyroscopeAgent.stop();

        verify(profilingScheduler, times(1)).stop(any());
    }

    @Test
    void startupTestWithDisabledAgent() {
        PyroscopeAgent.start(optionsAgentDisabled);

        verify(profilingScheduler, never()).start(any());
    }

}