package io.pyroscope.http;

public enum Format {
    JFR ("jfr");

    /**
     * Profile data format, as expected by Pyroscope's HTTP API.
     */
    public final String id;

    Format(String id) {
        this.id = id;
    }
}
