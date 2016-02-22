package io.bitsquare.p2p.storage.storageentry;

import com.google.common.annotations.VisibleForTesting;
import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.Sig;
import io.bitsquare.common.wire.Payload;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class ProtectedStorageEntry implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(ProtectedStorageEntry.class);

    protected final StoragePayload storagePayload;
    private final byte[] ownerPubKeyBytes;
    public transient PublicKey ownerPubKey;
    public int sequenceNumber;
    public byte[] signature;
    @VisibleForTesting
    transient public long timeStamp;

    public ProtectedStorageEntry(StoragePayload storagePayload, PublicKey ownerPubKey, int sequenceNumber, byte[] signature) {
        this.storagePayload = storagePayload;
        this.ownerPubKey = ownerPubKey;
        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.timeStamp = System.currentTimeMillis();
        this.ownerPubKeyBytes = new X509EncodedKeySpec(this.ownerPubKey.getEncoded()).getEncoded();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            ownerPubKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(ownerPubKeyBytes));
            updateTimeStamp();
        } catch (Throwable t) {
            log.error("Exception at readObject: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public StoragePayload getStoragePayload() {
        return storagePayload;
    }

    public void updateTimeStamp() {
        timeStamp = System.currentTimeMillis();
    }

    public void updateSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void updateSignature(byte[] signature) {
        this.signature = signature;
    }

    public boolean isExpired() {
        return (System.currentTimeMillis() - timeStamp) > storagePayload.getTTL();
    }

    @Override
    public String toString() {
        return "ProtectedData{" +
                "expirablePayload=" + storagePayload +
                ", timeStamp=" + timeStamp +
                ", sequenceNumber=" + sequenceNumber +
                ", ownerPubKey.hashCode()=" + ownerPubKey.hashCode() +
                ", signature.hashCode()=" + Arrays.toString(signature).hashCode() +
                '}';
    }

}
