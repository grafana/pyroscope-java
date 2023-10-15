package io.pyroscope.http;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public enum Units {
    SAMPLES ("samples"),
    OBJECTS ("objects"),
    BYTES ("bytes");

    /**
    * Pyroscope units id, as expected by Pyroscope's HTTP API.
    */
    public final String id;
}
