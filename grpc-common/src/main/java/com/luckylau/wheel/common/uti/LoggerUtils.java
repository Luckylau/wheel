package com.luckylau.wheel.common.uti;

import org.slf4j.Logger;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class LoggerUtils {
    public static final String TRACE = "TRACE";

    public static final String INFO = "INFO";

    public static final String DEBUG = "DEBUG";

    public static final String WARN = "WARN";

    public static final String ERROR = "ERROR";

    /**
     * Print if log debug level is enabled.
     *
     * @param logger logger
     * @param s      log message
     * @param args   arguments
     */
    public static void printIfDebugEnabled(Logger logger, String s, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(s, args);
        }
    }

    /**
     * Print if log info level is enabled.
     *
     * @param logger logger
     * @param s      log message
     * @param args   arguments
     */
    public static void printIfInfoEnabled(Logger logger, String s, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(s, args);
        }
    }

    /**
     * Print if log trace level is enabled.
     *
     * @param logger logger
     * @param s      log message
     * @param args   arguments
     */
    public static void printIfTraceEnabled(Logger logger, String s, Object... args) {
        if (logger.isTraceEnabled()) {
            logger.trace(s, args);
        }
    }

    /**
     * Print if log warn level is enabled.
     *
     * @param logger logger
     * @param s      log message
     * @param args   arguments
     */
    public static void printIfWarnEnabled(Logger logger, String s, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(s, args);
        }
    }

    /**
     * Print if log error level is enabled.
     *
     * @param logger logger
     * @param s      log message
     * @param args   arguments
     */
    public static void printIfErrorEnabled(Logger logger, String s, Object... args) {
        if (logger.isErrorEnabled()) {
            logger.error(s, args);
        }
    }
}
