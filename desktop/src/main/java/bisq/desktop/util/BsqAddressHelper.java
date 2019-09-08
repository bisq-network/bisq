package bisq.desktop.util;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;

public class BsqAddressHelper {

    private static final String prefix = "B";

    private final boolean useBsqAddressFormat;
    private final NetworkParameters networkParams;

    public BsqAddressHelper(boolean useBsqAddressFormat, NetworkParameters networkParams) {
        this.useBsqAddressFormat = useBsqAddressFormat;
        this.networkParams = networkParams;
    }

    /**
     * Returns the base-58 encoded String representation of this
     * object, including version and checksum bytes.
     */
    public String getBsqAddressStringFromAddress(Address address) {
        final String addressString = address.toString();
        if (useBsqAddressFormat)
            return prefix + addressString;
        else
            return addressString;
    }

    public Address getAddressFromBsqAddress(String encoded) {
        if (useBsqAddressFormat)
            encoded = encoded.substring(prefix.length(), encoded.length());

        try {
            return Address.fromBase58(networkParams, encoded);
        } catch (AddressFormatException e) {
            throw new RuntimeException(e);
        }
    }
}
