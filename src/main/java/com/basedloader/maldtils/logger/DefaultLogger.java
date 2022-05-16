package com.basedloader.maldtils.logger;

import java.io.PrintStream;

public class DefaultLogger implements Logger {
    public static final Logger INSTANCE = new DefaultLogger();
    private final PrintStream out = System.out;
    private final PrintStream err = System.err;

    @Override
    public void info(String msg) {
        out.println(msg);
    }

    @Override
    public void warn(String msg) {
        out.println(msg);
    }

    @Override
    public void error(String msg) {
        err.println(msg);
    }
}
