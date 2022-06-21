package io.pyroscope.javaagent.api;

public interface ConfigurationProvider {
    String get(String key);
}
