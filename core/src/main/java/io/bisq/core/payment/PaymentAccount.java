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

package io.bisq.core.payment;

import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.proto.ProtoCollectionUtil;
import io.bisq.common.proto.ProtoResolver;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@EqualsAndHashCode
@ToString
@Getter
@Slf4j
public abstract class PaymentAccount implements PersistablePayload {
    protected final PaymentMethod paymentMethod;
    protected final String id;
    protected final long creationDate;

    @Setter
    public PaymentAccountPayload paymentAccountPayload;

    @Setter
    protected String accountName;
    protected final List<TradeCurrency> tradeCurrencies = new ArrayList<>();
    @Setter
    protected TradeCurrency selectedTradeCurrency;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected PaymentAccount(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        id = UUID.randomUUID().toString();
        creationDate = new Date().getTime();
        paymentAccountPayload = getPayload();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PaymentAccount toProtoMessage() {
        PB.PaymentAccount.Builder builder = PB.PaymentAccount.newBuilder()
                .setPaymentMethod(paymentMethod.toProtoMessage())
                .setId(paymentMethod.getId())
                .setCreationDate(creationDate)
                .setPaymentAccountPayload((PB.PaymentAccountPayload) paymentAccountPayload.toProtoMessage())
                .setAccountName(accountName)
                .addAllTradeCurrencies(ProtoCollectionUtil.<PB.TradeCurrency>collectionToProto(tradeCurrencies));
        Optional.ofNullable(selectedTradeCurrency).ifPresent(selectedTradeCurrency -> builder.setSelectedTradeCurrency((PB.TradeCurrency) selectedTradeCurrency.toProtoMessage()));
        return builder.build();
    }

    public static PaymentAccount fromProto(PB.PaymentAccount proto, ProtoResolver protoResolver) {
        PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(PaymentMethod.getPaymentMethodById(proto.getPaymentMethod().getId()));
        paymentAccount.setAccountName(proto.getAccountName());
        paymentAccount.getTradeCurrencies().addAll(proto.getTradeCurrenciesList().stream().map(TradeCurrency::fromProto).collect(Collectors.toList()));
        paymentAccount.setSelectedTradeCurrency(paymentAccount.getSelectedTradeCurrency());
        paymentAccount.setPaymentAccountPayload((PaymentAccountPayload) protoResolver.fromProto(proto.getPaymentAccountPayload()));
        return paymentAccount;
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter, Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract PaymentAccountPayload getPayload();
}
