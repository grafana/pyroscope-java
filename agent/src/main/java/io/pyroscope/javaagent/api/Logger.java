package io.pyroscope.javaagent.api;

public interface Logger {
    enum Level {
        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3);
        public final int level;

        Level(int level) {
            this.level = level;
        }
    }

    void log(Level l, String msg, Object... args);
}
