/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.btc;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.persistance.Persistable;
import io.bisq.common.util.Utilities;
import io.bisq.wire.proto.Messages;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Optional;

/**
 * Every trade use a addressEntry with a dedicated address for all transactions related to the trade.
 * That way we have a kind of separated trade wallet, isolated from other transactions and avoiding coin merge.
 * If we would not avoid coin merge the user would lose privacy between trades.
 */
@EqualsAndHashCode
@Slf4j
@Getter
public final class AddressEntry implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public enum Context {
        ARBITRATOR,
        AVAILABLE,
        OFFER_FUNDING,
        RESERVED_FOR_TRADE,
        MULTI_SIG,
        TRADE_PAYOUT
    }

    // keyPair can be null in case the object is created from deserialization as it is transient.
    // It will be restored when the wallet is ready at setDeterministicKey
    // So after startup it never must be null
    @Nullable
    transient private DeterministicKey keyPair;
    @Nullable
    private final String offerId;
    private final Context context;
    private final byte[] pubKey;
    private final byte[] pubKeyHash;
    private final String paramId;
    @Nullable
    private Coin coinLockedInMultiSig;
    transient private NetworkParameters params;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AddressEntry(DeterministicKey keyPair, NetworkParameters params, Context context) {
        this(keyPair, params, context, null);
    }

    public AddressEntry(DeterministicKey keyPair, NetworkParameters params, Context context, @Nullable String offerId) {
        this.keyPair = keyPair;
        this.params = params;
        this.context = context;
        this.offerId = offerId;
        paramId = params.getId();
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

    public void setCoinLockedInMultiSig(Coin coinLockedInMultiSig) {
        this.coinLockedInMultiSig = coinLockedInMultiSig;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // For display we usually only display the first 8 characters.
    @Nullable
    public String getShortOfferId() {
        return offerId != null ? Utilities.getShortId(offerId) : null;
    }

    @Nullable
    public String getAddressString() {
        return getAddress() != null ? getAddress().toString() : null;
    }

    @Nullable
    public Address getAddress() {
        return keyPair != null ? keyPair.toAddress(params) : null;
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

    @Nullable
    public Coin getCoinLockedInMultiSig() {
        return coinLockedInMultiSig;
    }

    @Override
    public String toString() {
        return "AddressEntry{" +
                "offerId='" + getOfferId() + '\'' +
                ", context=" + context +
                ", address=" + getAddressString() +
                '}';
    }

    @Override
    public Message toProtobuf() {
        Messages.AddressEntry.Builder builder = Messages.AddressEntry.newBuilder()
                .setContext(Messages.AddressEntry.Context.valueOf(context.name()))
                .setPubkey(ByteString.copyFrom(pubKey))
                .setPubKeyHash(ByteString.copyFrom(pubKeyHash))
                .setParamId(paramId);
        Optional.ofNullable(offerId).ifPresent(builder::setOfferId);
        Optional.ofNullable(coinLockedInMultiSig).ifPresent(coinLockedInMultiSig -> {
            builder.setCoinLockedInMultiSig(Messages.Coin.newBuilder().setValue(coinLockedInMultiSig.getValue()));
        });
        return builder.build();
    }
}
