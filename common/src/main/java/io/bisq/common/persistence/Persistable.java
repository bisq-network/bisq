package io.bisq.common.persistence;

import com.google.protobuf.Parser;
import io.bisq.common.Marshaller;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Marker interface for data which is used for local data persistence
 */
public interface Persistable extends Marshaller {
    default Parser getParser() {
        throw new NotImplementedException("Protobuf getParser not yet implemented.");
    }
}
