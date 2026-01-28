package es.buni.hcb.utils;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public final class LoggingBootstrap {

    private LoggingBootstrap() {}

    public static void configure(boolean debug) {

        if (!debug) {
            LogManager.getLogManager().reset();
            Logger.getLogger("").setLevel(Level.OFF);

            System.setProperty("io.netty.noLogger", "true");

        } else {
            LogManager.getLogManager().reset();

            Logger root = Logger.getLogger("");
            root.setLevel(Level.INFO);

            for (var handler : root.getHandlers()) {
                handler.setLevel(Level.INFO);
            }

            System.clearProperty("io.netty.noLogger");

            Logger.getLogger("io.netty").setLevel(Level.FINE);
            Logger.getLogger("io.calimero").setLevel(Level.INFO);
        }
    }
}
