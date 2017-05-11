package io.bisq.common.storage;

import io.bisq.common.persistence.Persistable;

/**
 * Used to wrap a plaintext string to distinguish at file storage and safe it as plain text instead of a serialized java object.
 */
// We would not need Persistable but as it is used in Storage and Storage expects a Persistable as type we keep it...
// TODO use same like in the BSQChainState JSON persistence....
public class PlainTextWrapper implements Persistable {
    // That object is not saved to disc it is only of type Serializable to support the persistent framework.
    // SerialVersionUID has no relevance here.
    private static final long serialVersionUID = 0;

    public final String plainText;

    public PlainTextWrapper(String plainText) {
        this.plainText = plainText;
    }
}
