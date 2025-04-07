package io.pyroscope;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Preconditions {

    private Preconditions() {
    }

    public static <T> T checkNotNull(@Nullable T reference, @NotNull String paramName) {
        if (reference == null) {
            throw new NullPointerException(paramName + " cannot be null");
        }
        return reference;
    }
}
