package com.runjva.sourceforge.jsocks.protocol;

/**
 * Exception thrown by various socks classes to indicate errors with protocol or
 * unsuccessfull server responses.
 */
public class SocksException extends java.io.IOException {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * Construct a SocksException with given errorcode.
     * <p>
     * Tries to look up message which corresponds to this error code.
     *
     * @param errCode Error code for this exception.
     */
    public SocksException(int errCode) {
        this.errCode = errCode;
        lookupErrorString(errCode);
    }

    private void lookupErrorString(int errCode) {
        if ((errCode >> 16) == 0) {
            if (errCode <= serverReplyMessage.length) {
                errString = serverReplyMessage[errCode];
            } else {
                errString = UNASSIGNED_ERROR_MESSAGE;
            }
        } else {
            // Local error
            errCode = (errCode >> 16) - 1;
            if (errCode <= localErrorMessage.length) {
                errString = localErrorMessage[errCode];
            } else {
                errString = UNASSIGNED_ERROR_MESSAGE;
            }
        }
    }

    /**
     * Construct a SocksException with given error code, and a Throwable cause
     *
     * @param errCode
     * @param t       Nested exception for debugging purposes.
     */
    public SocksException(int errCode, Throwable t) {
        super(t);  // Java 1.6+
        this.errCode = errCode;
        lookupErrorString(errCode);
    }

    /**
     * Constructs a SocksException with given error code and message.
     *
     * @param errCode   Error code.
     * @param errString Error Message.
     */
    public SocksException(int errCode, String errString) {
        this.errCode = errCode;
        this.errString = errString;
    }

    public SocksException(int errCode, String string, Throwable t) {
        super(string, t);  // Java 1.6+
        this.errCode = errCode;
        this.errString = string;
    }

    /**
     * Get the error code associated with this exception.
     *
     * @return Error code associated with this exception.
     */
    public int getErrorCode() {
        return errCode;
    }

    /**
     * Get human readable representation of this exception.
     *
     * @return String represntation of this exception.
     */
    public String toString() {
        return errString;
    }

    static final String UNASSIGNED_ERROR_MESSAGE = "Unknown error message";

    static final String serverReplyMessage[] = {"Succeeded",
            "General SOCKS server failure",
            "Connection not allowed by ruleset", "Network unreachable",
            "Host unreachable", "Connection refused", "TTL expired",
            "Command not supported", "Address type not supported"};

    static final String localErrorMessage[] = {"SOCKS server not specified",
            "Unable to contact SOCKS server", "IO error",
            "None of Authentication methods are supported",
            "Authentication failed", "General SOCKS fault"};

    String errString;
    int errCode;

}// End of SocksException class

