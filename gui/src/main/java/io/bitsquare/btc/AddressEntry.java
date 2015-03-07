/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.DeterministicKey;

import java.io.Serializable;

import java.util.Arrays;

/**
 * Is a minimalistic wallet abstraction used to separate transactions between different activities like:
 * Registration, trade and arbiter deposit.
 */
public class AddressEntry implements Serializable {
    private static final long serialVersionUID = 5501603992599920416L;
    private transient DeterministicKey key;
    private final NetworkParameters params;
    private final AddressContext addressContext;
    private final String offerId;
    private final byte[] pubKeyHash;


    public AddressEntry(DeterministicKey key, NetworkParameters params, AddressContext addressContext) {
        this(key, params, addressContext, null);
    }

    public AddressEntry(DeterministicKey key, NetworkParameters params, AddressContext addressContext, String offerId) {
        this.key = key;
        this.params = params;
        this.addressContext = addressContext;
        this.offerId = offerId;

        pubKeyHash = key.getPubOnly().getPubKeyHash();
    }

    public String getOfferId() {
        return offerId;
    }

    public AddressContext getAddressContext() {
        return addressContext;
    }

    public String getAddressString() {
        return getAddress().toString();
    }

    public String getPubKeyAsHexString() {
        return Utils.HEX.encode(key.getPubKey());
    }

    public DeterministicKey getKey() {
        return key;
    }

    public Address getAddress() {
        return key.toAddress(params);
    }

    public void setDeterministicKey(DeterministicKey key) {
        this.key = key;
    }

    public byte[] getPubKeyHash() {
        return pubKeyHash;
    }

    public static enum AddressContext {
        REGISTRATION_FEE,
        TRADE,
        ARBITRATOR_DEPOSIT
    }

    @Override
    public String toString() {
        return "AddressEntry{" +
                "addressString=" + getAddress().toString() +
                "key=" + key +
                ", params=" + params +
                ", addressContext=" + addressContext +
                ", offerId='" + offerId + '\'' +
                ", pubKeyHash=" + Arrays.toString(pubKeyHash) +
                '}';
    }
}
