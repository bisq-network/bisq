package io.bitsquare.p2p;

import io.bitsquare.common.crypto.Hash;

import java.io.Serializable;
import java.util.regex.Pattern;

public class Address implements Serializable {
    public final String hostName;
    public final int port;
    transient private byte[] addressPrefixHash;

    public Address(String hostName, int port) {
        this.hostName = hostName;
        this.port = port;
    }

    public Address(String fullAddress) {
        final String[] split = fullAddress.split(Pattern.quote(":"));
        this.hostName = split[0];
        this.port = Integer.parseInt(split[1]);
    }

    public String getFullAddress() {
        return hostName + ":" + port;
    }

    // We use just a few chars form or address to blur the potential receiver for sent messages
    public byte[] getAddressPrefixHash() {
        if (addressPrefixHash == null)
            addressPrefixHash = Hash.getHash(getFullAddress().substring(0, 2));
        return addressPrefixHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Address)) return false;

        Address address = (Address) o;

        if (port != address.port) return false;
        return !(hostName != null ? !hostName.equals(address.hostName) : address.hostName != null);

    }

    @Override
    public int hashCode() {
        int result = hostName != null ? hostName.hashCode() : 0;
        result = 31 * result + port;
        return result;
    }

    @Override
    public String toString() {
        return getFullAddress();
    }
}
