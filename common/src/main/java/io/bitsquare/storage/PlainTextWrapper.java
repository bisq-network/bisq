package io.bitsquare.storage;

import java.io.Serializable;

/**
 * Used to wrap a plaintext string to distinguish at file storage and safe it as plain text instead of a serialized java object.
 */
public class PlainTextWrapper implements Serializable {
    // That object is not saved to disc it is only of type Serializable to support the persistent framework.
    // SerialVersionUID has no relevance here. 
    private static final long serialVersionUID = 0;

    public final String plainText;

    public PlainTextWrapper(String plainText) {
        this.plainText = plainText;
    }
}
