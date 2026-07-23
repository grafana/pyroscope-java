package io.pyroscope.javaagent.config;

import io.pyroscope.javaagent.impl.PropertiesConfigurationProvider;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class APDistributionTest {

    @Test
    void parse() {
        assertEquals(APDistribution.FORK, APDistribution.parse(null, APDistribution.FORK));
        assertEquals(APDistribution.FORK, APDistribution.parse("", APDistribution.FORK));
        assertEquals(APDistribution.FORK, APDistribution.parse("fork", APDistribution.GENUINE));
        assertEquals(APDistribution.GENUINE, APDistribution.parse("genuine", APDistribution.FORK));
        assertEquals(APDistribution.GENUINE, APDistribution.parse(" GENUINE ", APDistribution.FORK));
        assertEquals(APDistribution.FORK, APDistribution.parse("unknown", APDistribution.FORK));
    }

    @Test
    void configDefaults() {
        Config config = Config.build(new PropertiesConfigurationProvider(new Properties()));
        assertEquals(APDistribution.FORK, config.apDistribution);
        assertNull(config.apLibraryPath);
    }

    @Test
    void configFromProperties() {
        Properties props = new Properties();
        props.setProperty("PYROSCOPE_AP_DISTRIBUTION", "genuine");
        props.setProperty("PYROSCOPE_AP_LIBRARY_PATH", "/opt/libasyncProfiler.so");
        Config config = Config.build(new PropertiesConfigurationProvider(props));
        assertEquals(APDistribution.GENUINE, config.apDistribution);
        assertEquals("/opt/libasyncProfiler.so", config.apLibraryPath);
    }

    @Test
    void configFromBuilder() {
        Config config = new Config.Builder()
            .setAPDistribution(APDistribution.GENUINE)
            .setAPLibraryPath("/opt/libasyncProfiler.so")
            .build();
        assertEquals(APDistribution.GENUINE, config.apDistribution);
        assertEquals("/opt/libasyncProfiler.so", config.apLibraryPath);

        Config copy = config.newBuilder().build();
        assertEquals(APDistribution.GENUINE, copy.apDistribution);
        assertEquals("/opt/libasyncProfiler.so", copy.apLibraryPath);
    }
}
