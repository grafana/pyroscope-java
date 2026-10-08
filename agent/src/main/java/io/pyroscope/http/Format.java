package io.pyroscope.http;

public enum Format {
    JFR ("jfr"),
    /** CPU profiles served by the pull-mode HTTP endpoint. */
    PPROF ("pprof"),
    /** Experimental and unstable; the OTLP Profiles protocol may change incompatibly. */
    OTLP ("otlp");

    /**
     * Profile data format, as expected by Pyroscope's HTTP API.
     */
    public final String id;

    Format(String id) {
        this.id = id;
    }
}
