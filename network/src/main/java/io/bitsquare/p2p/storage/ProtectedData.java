package io.bitsquare.p2p.storage;

import com.google.common.annotations.VisibleForTesting;
import io.bitsquare.p2p.storage.messages.ExpirableMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Date;

public class ProtectedData implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(P2PDataStorage.class);

    public final ExpirableMessage expirableMessage;

    //TODO check if that field make sense as it is in expirableMessage.getTTL()
    transient public long ttl;

    public final PublicKey ownerPubKey;
    public final int sequenceNumber;
    public final byte[] signature;
    @VisibleForTesting
    transient public Date date;

    public ProtectedData(ExpirableMessage expirableMessage, long ttl, PublicKey ownerPubKey, int sequenceNumber, byte[] signature) {
        this.expirableMessage = expirableMessage;
        this.ttl = ttl;
        this.ownerPubKey = ownerPubKey;
        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.date = new Date();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            ttl = expirableMessage.getTTL();
            date = new Date();

        } catch (Throwable t) {
            log.error("Exception at readObject: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public boolean isExpired() {
        return (new Date().getTime() - date.getTime()) > ttl;
    }

    @Override
    public String toString() {
        return "ProtectedData{" +
                "expirablePayload=" + expirableMessage +
                ", ttl=" + ttl +
                ", date=" + date +
                ", sequenceNumber=" + sequenceNumber +
                ", ownerStoragePubKey.hashCode()=" + ownerPubKey.hashCode() +
                ", signature.hashCode()=" + Arrays.toString(signature).hashCode() +
                '}';
    }
}
