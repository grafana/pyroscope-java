package io.pyroscope.http;

public enum Units {
    SAMPLES ("samples"),
    OBJECTS ("objects"),
    BYTES ("bytes");

    /**
    * Pyroscope units id, as expected by Pyroscope's HTTP API.
    */
    public final String id;

    Units(String id) {
        this.id = id;
    }
}
