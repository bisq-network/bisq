package io.bisq.common.persistance;

import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import org.apache.commons.lang3.NotImplementedException;

import java.io.Serializable;

/**
 * Marker interface for data which is used for local data persistence
 */
public interface Persistable extends Serializable {

    default Message toProtobuf() {
        throw new NotImplementedException("toProtobuf not yet implemented.");
    }

    default Message fromProtobuf() {
        throw new NotImplementedException("fromProtobuf not yet implemented.");
    }

    default Parser getParser() {
        throw new NotImplementedException("Protobuf getParser not yet implemented.");
    }
}
