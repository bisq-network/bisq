package io.bitsquare.storage;

import java.io.Serializable;

/**
 * Used to wrap a json string to distinguish at file storage and safe it as plain text instead of a serialized java object.
 */
public class JsonString implements Serializable {
    private static final long serialVersionUID = 0;
    
    public final String json;

    public JsonString(String json) {
        this.json = json;
    }
}
