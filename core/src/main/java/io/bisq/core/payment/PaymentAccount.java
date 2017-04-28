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

import io.bisq.common.app.Version;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.proto.ProtoHelper;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.proto.ProtoUtil;
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
@Slf4j
public abstract class PaymentAccount implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    @Getter
    protected final String id;

    protected final long creationDate;
    @Getter
    protected final PaymentMethod paymentMethod;
    @Getter
    @Setter
    protected String accountName;
    @Getter
    final List<TradeCurrency> tradeCurrencies = new ArrayList<>();
    @Getter
    @Setter
    protected TradeCurrency selectedTradeCurrency;
    @Getter
    public final PaymentAccountPayload paymentAccountPayload;

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

    @Override
    public PB.PaymentAccount toProto() {
        PB.PaymentAccount.Builder builder = PB.PaymentAccount.newBuilder()
                .setId(paymentMethod.getId())
                .setCreationDate(creationDate)
                .setPaymentMethod(paymentMethod.toProto())
                .setAccountName(accountName)
                .addAllTradeCurrencies(ProtoHelper.collectionToProto(tradeCurrencies))
                .setPaymentAccountPayload((PB.PaymentAccountPayload) paymentAccountPayload.toProto());
        Optional.ofNullable(selectedTradeCurrency).ifPresent(selectedTradeCurrency -> builder.setSelectedTradeCurrency((PB.TradeCurrency) selectedTradeCurrency.toProto()));
        return builder.build();
    }

    // complicated: uses a factory to get the specific type, which then calls the ctor which does getPayload for id etc
    public static PaymentAccount fromProto(PB.PaymentAccount account) {
        PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(PaymentMethod.getPaymentMethodById(account.getPaymentMethod().getId()));
        paymentAccount.setAccountName(account.getAccountName());
        paymentAccount.getTradeCurrencies().addAll(account.getTradeCurrenciesList().stream().map(tradeCurrency -> TradeCurrency.fromProto(tradeCurrency)).collect(Collectors.toList()));
        paymentAccount.setSelectedTradeCurrency(paymentAccount.getSelectedTradeCurrency());
        return paymentAccount;
    }

}
