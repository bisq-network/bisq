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

import io.bitsquare.app.Version;
import io.bitsquare.common.persistance.Persistable;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Every trade use a addressEntry with a dedicated address for all transactions related to the trade.
 * That way we have a kind of separated trade wallet, isolated from other transactions and avoiding coin merge.
 * If we would not avoid coin merge the user would lose privacy between trades.
 */
public final class AddressEntry implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(AddressEntry.class);

    public enum Context {
        ARBITRATOR,

        AVAILABLE,

        OFFER_FUNDING,
        RESERVED_FOR_TRADE, //reserved
        MULTI_SIG, //locked
        TRADE_PAYOUT,

        DAO_SHARE,
        DAO_DIVIDEND
    }

    // keyPair can be null in case the object is created from deserialization as it is transient.
    // It will be restored when the wallet is ready at setDeterministicKey
    // So after startup it never must be null
    @Nullable
    transient private DeterministicKey keyPair;

    // Only set if its a TRADE Context
    @Nullable
    private final String offerId;

    private final Context context;
    private final byte[] pubKey;
    private final byte[] pubKeyHash;
    private final String paramId;
    @Nullable
    private Coin lockedTradeAmount;
    transient private NetworkParameters params;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    // If created without offerId (arbitrator)
    public AddressEntry(DeterministicKey keyPair, NetworkParameters params, Context context) {
        this(keyPair, params, context, null);
    }

    // If created with offerId
    public AddressEntry(@Nullable DeterministicKey keyPair, NetworkParameters params, Context context, @Nullable String offerId) {
        this.keyPair = keyPair;
        this.params = params;
        this.context = context;
        this.offerId = offerId;

        paramId = params.getId();

        checkNotNull(keyPair);
        pubKey = keyPair.getPubKey();
        pubKeyHash = keyPair.getPubKeyHash();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();

            if (MainNetParams.ID_MAINNET.equals(paramId))
                params = MainNetParams.get();
            else if (MainNetParams.ID_TESTNET.equals(paramId))
                params = TestNet3Params.get();
            else if (MainNetParams.ID_REGTEST.equals(paramId))
                params = RegTestParams.get();

        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    // Set after wallet is ready 
    public void setDeterministicKey(DeterministicKey deterministicKey) {
        this.keyPair = deterministicKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public String getOfferId() {
        return offerId;
    }

    // For display we usually only display the first 8 characters.
    @Nullable
    public String getShortOfferId() {
        return offerId != null ? offerId.substring(0, Math.min(8, offerId.length())) : null;
    }

    public Context getContext() {
        return context;
    }

    @Nullable
    public String getAddressString() {
        return getAddress() != null ? getAddress().toString() : null;
    }

    @Nullable
    public DeterministicKey getKeyPair() {
        return keyPair != null ? keyPair : null;
    }

    @Nullable
    public Address getAddress() {
        return keyPair != null ? keyPair.toAddress(params) : null;
    }

    public byte[] getPubKeyHash() {
        return pubKeyHash;
    }

    public byte[] getPubKey() {
        return pubKey;
    }

    public boolean isOpenOffer() {
        return context == Context.OFFER_FUNDING || context == Context.RESERVED_FOR_TRADE;
    }

    public boolean isTrade() {
        return context == Context.MULTI_SIG || context == Context.TRADE_PAYOUT;
    }

    public boolean isTradable() {
        return isOpenOffer() || isTrade();
    }

    public void setLockedTradeAmount(Coin lockedTradeAmount) {
        this.lockedTradeAmount = lockedTradeAmount;
    }

    @org.jetbrains.annotations.Nullable
    public Coin getLockedTradeAmount() {
        return lockedTradeAmount;
    }

    @Override
    public String toString() {
        return "AddressEntry{" +
                "offerId='" + offerId + '\'' +
                ", context=" + context +
                ", address=" + getAddressString() +
                '}';
    }
}
