package io.bitsquare.p2p.storage.data;

import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.Sig;
import io.bitsquare.common.wire.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class RefreshTTLBundle implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(RefreshTTLBundle.class);

    transient public PublicKey ownerPubKey;
    public final byte[] hashOfDataAndSeqNr;
    public final byte[] signature;
    public final byte[] hashOfPayload;
    public final int sequenceNumber;
    private byte[] ownerPubKeyBytes;

    public RefreshTTLBundle(PublicKey ownerPubKey,
                            byte[] hashOfDataAndSeqNr,
                            byte[] signature,
                            byte[] hashOfPayload,
                            int sequenceNumber) {
        this.ownerPubKey = ownerPubKey;
        this.hashOfDataAndSeqNr = hashOfDataAndSeqNr;
        this.signature = signature;
        this.hashOfPayload = hashOfPayload;
        this.sequenceNumber = sequenceNumber;

        ownerPubKeyBytes = new X509EncodedKeySpec(ownerPubKey.getEncoded()).getEncoded();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            ownerPubKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(ownerPubKeyBytes));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        } catch (Throwable t) {
            log.trace("Cannot be deserialized." + t.getMessage());
        }
    }

    @Override
    public String toString() {
        return "RefreshTTLPackage{" +
                "ownerPubKey.hashCode()=" + ownerPubKey.hashCode() +
                ", hashOfDataAndSeqNr.hashCode()=" + Arrays.hashCode(hashOfDataAndSeqNr) +
                ", hashOfPayload.hashCode()=" + Arrays.hashCode(hashOfPayload) +
                ", sequenceNumber=" + sequenceNumber +
                ", signature.hashCode()=" + Arrays.hashCode(signature) +
                '}';
    }
}
