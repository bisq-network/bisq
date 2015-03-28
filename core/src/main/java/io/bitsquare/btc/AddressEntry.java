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
import org.bitcoinj.crypto.DeterministicKey;

import java.io.Serializable;

import java.util.Arrays;

import org.jetbrains.annotations.NotNull;

/**
 * Is a minimalistic wallet abstraction used to separate transactions between different activities like:
 * Registration, trade and arbiter deposit.
 */
public class AddressEntry implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    // that will be restored from the wallet at deserialisation
    private transient DeterministicKey keyPair;

    private final String offerId;
    private final Context context;
    private final byte[] pubKey;
    private final byte[] pubKeyHash;
    private final NetworkParameters params;

    public AddressEntry(DeterministicKey keyPair, NetworkParameters params, @SuppressWarnings("SameParameterValue") Context context) {
        this(keyPair, params, context, null);
    }

    public AddressEntry(DeterministicKey keyPair, NetworkParameters params, Context context, String offerId) {
        this.keyPair = keyPair;
        this.params = params;
        this.context = context;
        this.offerId = offerId;

        pubKey = keyPair.getPubKey();
        pubKeyHash = keyPair.getPubKeyHash();
    }

    public String getOfferId() {
        return offerId;
    }

    public Context getContext() {
        return context;
    }

    public String getAddressString() {
        return getAddress().toString();
    }

    @NotNull
    public DeterministicKey getKeyPair() {
        return keyPair;
    }

    public Address getAddress() {
        return keyPair.toAddress(params);
    }

    public void setDeterministicKey(DeterministicKey deterministicKey) {
        this.keyPair = deterministicKey;
    }

    public byte[] getPubKeyHash() {
        return pubKeyHash;
    }

    public byte[] getPubKey() {
        return pubKey;
    }

    public enum Context {
        REGISTRATION_FEE,
        TRADE,
        ARBITRATOR_DEPOSIT
    }

    @Override
    public String toString() {
        return "AddressEntry{" +
                "offerId='" + offerId + '\'' +
                ", context=" + context +
                ", pubKey=" + Arrays.toString(pubKey) +
                ", pubKeyHash=" + Arrays.toString(pubKeyHash) +
                ", params=" + params +
                '}';
    }
}
