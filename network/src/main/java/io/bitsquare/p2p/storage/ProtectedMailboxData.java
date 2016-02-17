package io.bitsquare.p2p.storage;

import io.bitsquare.p2p.storage.messages.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class ProtectedMailboxData extends ProtectedData {
    private static final Logger log = LoggerFactory.getLogger(P2PDataStorage.class);

    public final PublicKey receiversPubKey;

    public ProtectedMailboxData(MailboxMessage data, long ttl, PublicKey ownerStoragePubKey, int sequenceNumber, byte[] signature, PublicKey receiversPubKey) {
        super(data, ttl, ownerStoragePubKey, sequenceNumber, signature);

        this.receiversPubKey = receiversPubKey;
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            ttl = expirableMessage.getTTL();

            // in case the reported creation date is in the future 
            // we reset the date to the current time
            if (date.getTime() > new Date().getTime()) {
                if (date.getTime() > new Date().getTime() + TimeUnit.MINUTES.toMillis(1))
                    log.warn("Date of object is more then a minute in future. " +
                            "That might be ok as peers clocks are not synced but could be also a spam attack.\n" +
                            "date=" + date + " / now=" + new Date());
                else
                    log.debug("Date of object is slightly future. " +
                            "That is probably because peers clocks are not synced.\n" +
                            "date=" + date + " / now=" + new Date());
                date = new Date();
            }
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
        return "ProtectedMailboxData{" +
                "receiversPubKey.hashCode()=" + receiversPubKey.hashCode() +
                "} " + super.toString();
    }
}
