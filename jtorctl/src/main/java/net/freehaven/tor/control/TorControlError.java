// Copyright 2005 Nick Mathewson, Roger Dingledine
// See LICENSE file for copying information
package net.freehaven.tor.control;

import java.io.IOException;

/**
 * An exception raised when Tor tells us about an error.
 */
public class TorControlError extends IOException {

    static final long serialVersionUID = 3;

    private final int errorType;

    public TorControlError(int type, String s) {
        super(s);
        errorType = type;
    }

    public TorControlError(String s) {
        this(-1, s);
    }

    public int getErrorType() {
        return errorType;
    }

    public String getErrorMsg() {
        try {
            if (errorType == -1)
                return null;
            return TorControlCommands.ERROR_MSGS[errorType];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return "Unrecongized error #" + errorType;
        }
    }
}

