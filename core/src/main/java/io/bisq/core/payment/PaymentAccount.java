/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.payment;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.CryptoUtils;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.common.util.Utilities;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode
@ToString
@Getter
@Slf4j
public abstract class PaymentAccount implements PersistablePayload {
    protected final PaymentMethod paymentMethod;
    @Setter
    protected String id;
    @Setter
    protected long creationDate;

    @Setter
    public PaymentAccountPayload paymentAccountPayload;

    @Setter
    protected String accountName;
    protected final List<TradeCurrency> tradeCurrencies = new ArrayList<>();
    @Setter
    @Nullable
    protected TradeCurrency selectedTradeCurrency;

    @Setter
    @Nullable
    protected PaymentAccountAgeWitness paymentPaymentAccountAgeWitness;

    // TODO add to PB!
    @Setter
    @Nullable
    protected byte[] salt;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected PaymentAccount(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public void init() {
        id = UUID.randomUUID().toString();
        creationDate = new Date().getTime();
        paymentAccountPayload = createPayload();
        setRandomSalt();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PaymentAccount toProtoMessage() {
        checkNotNull(accountName, "accountName must not be null");
        PB.PaymentAccount.Builder builder = PB.PaymentAccount.newBuilder()
                .setPaymentMethod(paymentMethod.toProtoMessage())
                .setId(id)
                .setCreationDate(creationDate)
                .setPaymentAccountPayload((PB.PaymentAccountPayload) paymentAccountPayload.toProtoMessage())
                .setAccountName(accountName)
                .addAllTradeCurrencies(ProtoUtil.<PB.TradeCurrency>collectionToProto(tradeCurrencies));
        Optional.ofNullable(selectedTradeCurrency).ifPresent(selectedTradeCurrency -> builder.setSelectedTradeCurrency((PB.TradeCurrency) selectedTradeCurrency.toProtoMessage()));
        Optional.ofNullable(salt).ifPresent(salt -> builder.setSalt(ByteString.copyFrom(salt)));
        return builder.build();
    }

    public static PaymentAccount fromProto(PB.PaymentAccount proto, CoreProtoResolver coreProtoResolver) {
        PaymentAccount account = PaymentAccountFactory.getPaymentAccount(PaymentMethod.getPaymentMethodById(proto.getPaymentMethod().getId()));
        account.getTradeCurrencies().clear();
        account.setId(proto.getId());
        account.setCreationDate(proto.getCreationDate());
        account.setAccountName(proto.getAccountName());
        account.getTradeCurrencies().addAll(proto.getTradeCurrenciesList().stream().map(TradeCurrency::fromProto).collect(Collectors.toList()));
        account.setPaymentAccountPayload(coreProtoResolver.fromProto(proto.getPaymentAccountPayload()));

        if (proto.hasSelectedTradeCurrency())
            account.setSelectedTradeCurrency(TradeCurrency.fromProto(proto.getSelectedTradeCurrency()));

        // We don't set if it is null (we dont want to overwrite random salt generated at init
        if (!proto.getSalt().isEmpty())
            account.setSalt(proto.getSalt().toByteArray());
        else
            account.setRandomSalt();

        return account;
    }

    private void setRandomSalt() {
        // We set by default a random salt. 
        // User can set salt as well by hex string.
        // Persisted value will overwrite that
        salt = CryptoUtils.getSalt(32); // 256 bit
    }

    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Date getCreationDate() {
        return new Date(creationDate);
    }

    public void addCurrency(TradeCurrency tradeCurrency) {
        if (!tradeCurrencies.contains(tradeCurrency))
            tradeCurrencies.add(tradeCurrency);
    }

    public void removeCurrency(TradeCurrency tradeCurrency) {
        if (tradeCurrencies.contains(tradeCurrency))
            tradeCurrencies.remove(tradeCurrency);
    }

    public boolean hasMultipleCurrencies() {
        return tradeCurrencies.size() > 1;
    }

    public void setSingleTradeCurrency(TradeCurrency tradeCurrency) {
        tradeCurrencies.clear();
        tradeCurrencies.add(tradeCurrency);
        setSelectedTradeCurrency(tradeCurrency);
    }

    @Nullable
    public TradeCurrency getSingleTradeCurrency() {
        if (!tradeCurrencies.isEmpty())
            return tradeCurrencies.get(0);
        else
            return null;
    }

    protected abstract PaymentAccountPayload createPayload();

    public void setSalt(byte[] salt) {
        this.salt = salt;
    }

    public void setSaltAsHex(String saltAsHex) {
        this.salt = Utilities.decodeFromHex(saltAsHex);
    }

    public String getSaltAsHex() {
        return Utilities.bytesAsHexString(salt);
    }
}
