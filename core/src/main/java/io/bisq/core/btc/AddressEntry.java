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
import io.bisq.common.persistence.Persistable;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.wallet.WalletUtils;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.crypto.DeterministicKey;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Every trade use a addressEntry with a dedicated address for all transactions related to the trade.
 * That way we have a kind of separated trade wallet, isolated from other transactions and avoiding coin merge.
 * If we would not avoid coin merge the user would lose privacy between trades.
 */
@EqualsAndHashCode
@Slf4j
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
    @Getter
    private final String offerId;
    @Getter
    private final Context context;
    @Getter
    private final byte[] pubKey;
    @Getter
    private final byte[] pubKeyHash;

    @Nullable
    private long coinLockedInMultiSig;

    @Nullable
    transient private DeterministicKey keyPair;
    @Nullable
    transient private Address address;
    @Nullable
    transient private String addressString;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AddressEntry(DeterministicKey keyPair, Context context) {
        this(keyPair, context, null);
    }

    public AddressEntry(@NotNull DeterministicKey keyPair,
                        Context context,
                        @Nullable String offerId) {
        this.keyPair = keyPair;
        this.context = context;
        this.offerId = offerId;
        pubKey = keyPair.getPubKey();
        pubKeyHash = keyPair.getPubKeyHash();
    }

    // called from Resolver
    public AddressEntry(byte[] pubKey,
                        byte[] pubKeyHash,
                        Context context,
                        @Nullable String offerId,
                        @Nullable Coin coinLockedInMultiSig) {
        this.pubKey = pubKey;
        this.pubKeyHash = pubKeyHash;
        this.context = context;
        this.offerId = offerId;
        this.coinLockedInMultiSig = coinLockedInMultiSig.value;
    }

    // Set after wallet is ready
    public void setDeterministicKey(DeterministicKey deterministicKey) {
        this.keyPair = deterministicKey;
    }

    // getKeyPair must not be called before wallet is ready (in case we get the object recreated from disk deserialization)
    // If the object is created at runtime it must be always constructed after wallet is ready.
    @NotNull
    public DeterministicKey getKeyPair() {
        checkNotNull(keyPair, "keyPair must not be null. If we got the addressEntry created from PB we need to have " +
                "setDeterministicKey got called before any access with getKeyPair().");
        return keyPair;
    }

    public void setCoinLockedInMultiSig(@NotNull Coin coinLockedInMultiSig) {
        this.coinLockedInMultiSig = coinLockedInMultiSig.value;
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
        if (addressString == null && getAddress() != null)
            addressString = getAddress().toString();
        return addressString;
    }

    @Nullable
    public Address getAddress() {
        if (address == null && keyPair != null)
            address = keyPair.toAddress(WalletUtils.getParameters());
        return address;
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

    public Coin getCoinLockedInMultiSig() {
        return Coin.valueOf(coinLockedInMultiSig);
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
    public Message toProtoMessage() {
        PB.AddressEntry.Builder builder = PB.AddressEntry.newBuilder()
                .setContext(PB.AddressEntry.Context.valueOf(context.name()))
                .setPubKey(ByteString.copyFrom(pubKey))
                .setCoinLockedInMultiSig(coinLockedInMultiSig)
                .setPubKeyHash(ByteString.copyFrom(pubKeyHash));
        Optional.ofNullable(offerId).ifPresent(builder::setOfferId);
        return builder.build();
    }
}
