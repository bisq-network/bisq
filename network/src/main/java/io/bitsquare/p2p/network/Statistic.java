package io.bitsquare.p2p.network;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class Statistic {
    private static final Logger log = LoggerFactory.getLogger(Statistic.class);

    private final Date creationDate;
    private long lastActivityTimestamp;
    private int sentBytes = 0;
    private int receivedBytes = 0;

    public LongProperty lastActivityTimestampProperty = new SimpleLongProperty(System.currentTimeMillis());
    public IntegerProperty sentBytesProperty = new SimpleIntegerProperty(0);
    public IntegerProperty receivedBytesProperty = new SimpleIntegerProperty(0);

    public Statistic() {
        creationDate = new Date();
        updateLastActivityTimestamp();
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void updateLastActivityTimestamp() {
        lastActivityTimestamp = System.currentTimeMillis();
        lastActivityTimestampProperty.set(lastActivityTimestamp);
    }

    public long getLastActivityTimestamp() {
        return lastActivityTimestamp;
    }

    public int getSentBytes() {
        return sentBytes;
    }

    public void addSentBytes(int sentBytes) {
        this.sentBytes += sentBytes;
        sentBytesProperty.set(this.sentBytes);
    }

    public int getReceivedBytes() {
        return receivedBytes;
    }

    public void addReceivedBytes(int receivedBytes) {
        this.receivedBytes += receivedBytes;
        receivedBytesProperty.set(this.receivedBytes);
    }
}
