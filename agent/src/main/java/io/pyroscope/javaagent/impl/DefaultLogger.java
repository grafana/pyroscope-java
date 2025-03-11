package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.api.Logger;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class DefaultLogger implements Logger {
    public static final Logger PRECONFIG_LOGGER = new DefaultLogger(Level.DEBUG, System.err);
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final Level logLevel;
    private final PrintStream out;

    public DefaultLogger(Level logLevel, PrintStream out) {
        this.logLevel = logLevel;
        this.out = out;
    }

    @Override
	public void log(Level logLevel, String msg, Object... args) {
		if (logLevel.level < this.logLevel.level) {
			return;
		}
		String date;
		synchronized (this) {
			date = DATE_FORMAT.format(System.currentTimeMillis());
		}
		String formattedMsg = (msg == null) ? "null"
				: (args == null || args.length == 0) ? msg : String.format(msg, args);

		out.printf("%s [%s] %s%n", date, logLevel, formattedMsg);
	}

}
