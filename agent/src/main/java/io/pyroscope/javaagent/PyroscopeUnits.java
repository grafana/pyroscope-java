package io.pyroscope.javaagent;

public enum PyroscopeUnits {
    SAMPLES ("samples"),
    OBJECTS ("objects"),
    BYTES ("bytes");

    /**
    * Pyroscope units id, as expected by Pyroscope's HTTP API.
    */
    public final String id;

    PyroscopeUnits(String id) {
        this.id = id;
    }
}
