package io.bitsquare.p2p.storage.data;

import java.io.Serializable;

public class DataAndSeqNr implements Serializable {
    public final Serializable data;
    public final int sequenceNumber;

    public DataAndSeqNr(Serializable data, int sequenceNumber) {
        this.data = data;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataAndSeqNr)) return false;

        DataAndSeqNr that = (DataAndSeqNr) o;

        if (sequenceNumber != that.sequenceNumber) return false;
        return !(data != null ? !data.equals(that.data) : that.data != null);

    }

    @Override
    public int hashCode() {
        int result = data != null ? data.hashCode() : 0;
        result = 31 * result + sequenceNumber;
        return result;
    }
}
