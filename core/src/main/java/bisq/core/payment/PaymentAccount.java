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

package bisq.core.payment;

import bisq.core.locale.TradeCurrency;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.proto.CoreProtoResolver;

import bisq.common.proto.ProtoUtil;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.payment.payload.PaymentMethod.TRANSFERWISE_ID;
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
    @Setter
    protected String persistedAccountName;

    protected final List<TradeCurrency> tradeCurrencies = new ArrayList<>();
    @Setter
    @Nullable
    protected TradeCurrency selectedTradeCurrency;


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
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.PaymentAccount toProtoMessage() {
        checkNotNull(accountName, "accountName must not be null");
        protobuf.PaymentAccount.Builder builder = protobuf.PaymentAccount.newBuilder()
                .setPaymentMethod(paymentMethod.toProtoMessage())
                .setId(id)
                .setCreationDate(creationDate)
                .setPaymentAccountPayload((protobuf.PaymentAccountPayload) paymentAccountPayload.toProtoMessage())
                .setAccountName(accountName)
                .addAllTradeCurrencies(ProtoUtil.collectionToProto(tradeCurrencies, protobuf.TradeCurrency.class));
        Optional.ofNullable(selectedTradeCurrency).ifPresent(selectedTradeCurrency -> builder.setSelectedTradeCurrency((protobuf.TradeCurrency) selectedTradeCurrency.toProtoMessage()));
        return builder.build();
    }

    public static PaymentAccount fromProto(protobuf.PaymentAccount proto, CoreProtoResolver coreProtoResolver) {
        String paymentMethodId = proto.getPaymentMethod().getId();
        List<TradeCurrency> tradeCurrencies = proto.getTradeCurrenciesList().stream()
                .map(TradeCurrency::fromProto)
                .collect(Collectors.toList());

        // We need to remove NGN for Transferwise
        Optional<TradeCurrency> ngnTwOptional = tradeCurrencies.stream()
                .filter(e -> paymentMethodId.equals(TRANSFERWISE_ID))
                .filter(e -> e.getCode().equals("NGN"))
                .findAny();
        // We cannot remove it in the stream as it would cause a concurrentModificationException
        ngnTwOptional.ifPresent(tradeCurrencies::remove);

        try {
            PaymentAccount account = PaymentAccountFactory.getPaymentAccount(PaymentMethod.getPaymentMethod(paymentMethodId));
            account.getTradeCurrencies().clear();
            account.setId(proto.getId());
            account.setCreationDate(proto.getCreationDate());
            account.setAccountName(proto.getAccountName());
            account.setPersistedAccountName(proto.getAccountName());
            account.getTradeCurrencies().addAll(tradeCurrencies);
            account.setPaymentAccountPayload(coreProtoResolver.fromProto(proto.getPaymentAccountPayload()));

            if (proto.hasSelectedTradeCurrency())
                account.setSelectedTradeCurrency(TradeCurrency.fromProto(proto.getSelectedTradeCurrency()));

            return account;
        } catch (RuntimeException e) {
            log.warn("Could not load account: {}, exception: {}", paymentMethodId, e.toString());
            return null;
        }
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
        if (tradeCurrencies.size() == 1)
            return tradeCurrencies.get(0);
        else
            return null;
    }

    public long getMaxTradePeriod() {
        return paymentMethod.getMaxTradePeriod();
    }

    protected abstract PaymentAccountPayload createPayload();

    public void setSalt(byte[] salt) {
        paymentAccountPayload.setSalt(salt);
    }

    public byte[] getSalt() {
        return paymentAccountPayload.getSalt();
    }

    public void setSaltAsHex(String saltAsHex) {
        setSalt(Utilities.decodeFromHex(saltAsHex));
    }

    public String getSaltAsHex() {
        return Utilities.bytesAsHexString(getSalt());
    }

    public String getOwnerId() {
        return paymentAccountPayload.getOwnerId();
    }

    public boolean isCountryBasedPaymentAccount() {
        return this instanceof CountryBasedPaymentAccount;
    }

    public boolean hasPaymentMethodWithId(String paymentMethodId) {
        return this.getPaymentMethod().getId().equals(paymentMethodId);
    }

    /**
     * Return an Optional of the trade currency for this payment account, or
     * Optional.empty() if none is found.  If this payment account has a selected
     * trade currency, that is returned, else its single trade currency is returned,
     * else the first trade currency in this payment account's tradeCurrencies
     * list is returned.
     *
     * @return Optional of the trade currency for the given payment account
     */
    public Optional<TradeCurrency> getTradeCurrency() {
        if (this.getSelectedTradeCurrency() != null)
            return Optional.of(this.getSelectedTradeCurrency());
        else if (this.getSingleTradeCurrency() != null)
            return Optional.of(this.getSingleTradeCurrency());
        else if (!this.getTradeCurrencies().isEmpty())
            return Optional.of(this.getTradeCurrencies().get(0));
        else
            return Optional.empty();
    }

    public void onAddToUser() {
        // We are in the process to get added to the user. This is called just before saving the account and the
        // last moment we could apply some special handling if needed (e.g. as it happens for Revolut)
    }

    public String getPreTradeMessage(boolean isBuyer) {
        if (isBuyer) {
            return getMessageForBuyer();
        } else {
            return getMessageForSeller();
        }
    }

    // will be overridden by specific account when necessary
    public String getMessageForBuyer() {
        return null;
    }

    // will be overridden by specific account when necessary
    public String getMessageForSeller() {
        return null;
    }

    // will be overridden by specific account when necessary
    public String getMessageForAccountCreation() {
        return null;
    }

    public void onPersistChanges() {
        setPersistedAccountName(getAccountName());
    }

    public void revertChanges() {
        setAccountName(getPersistedAccountName());
    }
}
