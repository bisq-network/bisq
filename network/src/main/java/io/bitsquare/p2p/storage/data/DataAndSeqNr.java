package io.bitsquare.p2p.storage.data;

import java.io.Serializable;

public class DataAndSeqNr implements Serializable {
    // data are only used for getting cryptographic hash from both values
    private final Serializable data;
    private final int sequenceNumber;

    public DataAndSeqNr(Serializable data, int sequenceNumber) {
        this.data = data;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String toString() {
        return "DataAndSeqNr{" +
                "data=" + data +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
