// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

package com.daimler.pipeliner


import java.io.PrintStream;

/**
 * Provides Logging functionality
 * In addition to the standard streams, the logs can be published to Jenkins console.
 * init() should be called with appropriate input to initialize the logger prior to usage.
 *
 * @author Sumit Ahuja
 */

public class Logger {

    // TODO: Since Jenkins does not expose a stream directly, writing to Jenkins
    // is handled differently. If it is possible, then there is no need to
    // handle it separately.

    private static PrintStream out = null
    private static PrintStream err = null
    // Jenkins scripted environment reference
    private static def script = null

    /**
     * Initializes logger
     *
     * @param handler either Jenkins scripted environment reference or
     * Output Stream
     */
    public static init(handler) {
        if (handler instanceof OutputStream) {
            init(handler, handler);
        } else {
            script = handler
        }
    }

    /**
     * Initializes logger
     *
     * @param out_str Output Stream
     * @param err_str Error Stream
     */
    public static init(PrintStream out_str, PrintStream err_str) {
        out = out_str
        err = err_str
    }

    /**
     * Reset the logger
     */
    public static clear() {
        out = null
        err = null
        script = null
    }

    /**
     * Provides reference to Jenkins steps or stream as per initialization
     * Throws NullPointerException if the setup is not done
     *
     * @return script reference or output stream
     */
    private static getStream() {
        if (script != null) {
            return script
        }else if (out != null) {
            return out
        }

        throw new NullPointerException("Logger should be initialized prior to usage")
    }

    /**
     * Provides reference to Jenkins script or error stream as per initialization
     * Throws NullPointerException if logger is not setup previously
     *
     * @return script reference or error stream
     */
    private static getErrorStream() {
        if (script != null)
            return script
        else if (err != null)
            return err

        throw new NullPointerException("Logger should be initialized prior to usage")
    }

    /**
     * Log messages with info label
     *
     * @param string message
     */
    public static void info(String message) {
        getStream().println("INFO: " + message);
    }

    /**
     * Log messages with warn label
     *
     * @param string message
     */
    public static void warn(String message) {
        getStream().println("WARN: " + message);
    }

    /**
     * Log messages with error label
     *
     * @param string message
     */
    public static void error(String message) {
       getErrorStream().println("ERROR: " + message);
    }
}
