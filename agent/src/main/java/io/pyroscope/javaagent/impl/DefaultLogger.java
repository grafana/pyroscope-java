package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.Logger;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class DefaultLogger implements Logger {
    public static final DefaultLogger PRECONFIG_LOGGER = new DefaultLogger(Level.DEBUG, System.err);
    static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    final Level l;
    final PrintStream out;

    public DefaultLogger(Level l, PrintStream out) {
        this.l = l;
        this.out = out;
    }

    @Override
    public void log(Level l, String msg, Object... args) {
        if (l.level < this.l.level) {
            return;
        }
        String date;
        synchronized (this) {
            date = DATE_FORMAT.format(System.currentTimeMillis());
        }
        String msg2 = String.format(msg, args);

        out.printf("%s [%s] %s\n", date, l, msg2);
    }

}
