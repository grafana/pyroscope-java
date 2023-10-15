package io.pyroscope.http;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public enum Format {
    @Deprecated // use jfr
    COLLAPSED ("collapsed"),
    JFR ("jfr");

    /**
     * Profile data format, as expected by Pyroscope's HTTP API.
     */
    public final String id;
}
