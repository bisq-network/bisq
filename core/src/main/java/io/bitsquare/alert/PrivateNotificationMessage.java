package io.bitsquare.alert;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.messaging.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class PrivateNotificationMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final Logger log = LoggerFactory.getLogger(PrivateNotificationMessage.class);
    private NodeAddress myNodeAddress;
    public PrivateNotification privateNotification;
    private final String uid = UUID.randomUUID().toString();
    private final int messageVersion = Version.getP2PMessageVersion();

    public PrivateNotificationMessage(PrivateNotification privateNotification, NodeAddress myNodeAddress) {
        this.myNodeAddress = myNodeAddress;
        this.privateNotification = privateNotification;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return myNodeAddress;
    }

    @Override
    public String getUID() {
        return uid;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrivateNotificationMessage)) return false;

        PrivateNotificationMessage that = (PrivateNotificationMessage) o;

        if (messageVersion != that.messageVersion) return false;
        if (myNodeAddress != null ? !myNodeAddress.equals(that.myNodeAddress) : that.myNodeAddress != null)
            return false;
        if (privateNotification != null ? !privateNotification.equals(that.privateNotification) : that.privateNotification != null)
            return false;
        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);

    }

    @Override
    public int hashCode() {
        int result = myNodeAddress != null ? myNodeAddress.hashCode() : 0;
        result = 31 * result + (privateNotification != null ? privateNotification.hashCode() : 0);
        result = 31 * result + (uid != null ? uid.hashCode() : 0);
        result = 31 * result + messageVersion;
        return result;
    }

    @Override
    public String toString() {
        return "PrivateNotificationMessage{" +
                "myNodeAddress=" + myNodeAddress +
                ", privateNotification=" + privateNotification +
                ", uid='" + uid + '\'' +
                ", messageVersion=" + messageVersion +
                '}';
    }
}
