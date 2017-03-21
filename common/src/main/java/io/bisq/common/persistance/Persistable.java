package io.bisq.common.persistance;

import com.google.protobuf.Message;
import org.apache.commons.lang3.NotImplementedException;

import java.io.Serializable;

/**
 * Marker interface for data which is used for local data persistence
 */
public interface Persistable extends Serializable {

    default Message toProtobuf() {
        throw new NotImplementedException("Protobuf not yet implemented.");
    }
}
