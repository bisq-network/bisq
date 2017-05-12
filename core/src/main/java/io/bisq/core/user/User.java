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

package io.bisq.core.user;

import io.bisq.common.crypto.KeyRing;
import io.bisq.common.locale.LanguageUtil;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.storage.Storage;
import io.bisq.core.alert.Alert;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.arbitration.Mediator;
import io.bisq.core.filter.Filter;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.network.p2p.NodeAddress;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * The User is persisted locally.
 * It must never be transmitted over the wire (messageKeyPair contains private key!).
 */
@Slf4j
@AllArgsConstructor
public final class User {
    private UserPayload userPayload;

    // Transient immutable fields
    transient final private Storage<UserPayload> storage;
    transient final private KeyRing keyRing;
    transient private Set<TradeCurrency> tradeCurrenciesInPaymentAccounts;

    // Observable wrappers
    transient private ObservableSet<PaymentAccount> paymentAccountsAsObservable;
    transient private ObjectProperty<PaymentAccount> currentPaymentAccountProperty;

    @Inject
    public User(Storage<UserPayload> storage, KeyRing keyRing) throws NoSuchAlgorithmException {
        this.storage = storage;
        this.keyRing = keyRing;
    }

    // for unit tests
    public User() {
        storage = null;
        keyRing = null;
    }

    public void init() {
        UserPayload persisted = storage.initAndGetPersistedWithFileName("UserVO");
        userPayload = persisted != null ? persisted : new UserPayload();

        paymentAccountsAsObservable = FXCollections.observableSet(userPayload.getPaymentAccounts());
        currentPaymentAccountProperty = new SimpleObjectProperty<>(userPayload.getCurrentPaymentAccount());
        userPayload.setAccountID(String.valueOf(Math.abs(keyRing.getPubKeyRing().hashCode())));
        // language setup
        userPayload.getAcceptedLanguageLocaleCodes().add(LanguageUtil.getDefaultLanguageLocaleAsCode());
        String english = LanguageUtil.getEnglishLanguageLocaleCode();
        if (!userPayload.getAcceptedLanguageLocaleCodes().contains(english))
            userPayload.getAcceptedLanguageLocaleCodes().add(english);

        storage.queueUpForSave();

        // Use that to guarantee update of the serializable field and to make a storage update in case of a change
        paymentAccountsAsObservable.addListener((SetChangeListener<PaymentAccount>) change -> {
            userPayload.setPaymentAccounts(new HashSet<>(paymentAccountsAsObservable));
            tradeCurrenciesInPaymentAccounts = userPayload.getPaymentAccounts().stream().flatMap(e -> e.getTradeCurrencies().stream()).collect(Collectors.toSet());
            storage.queueUpForSave();
        });
        currentPaymentAccountProperty.addListener((ov) -> {
            userPayload.setCurrentPaymentAccount(currentPaymentAccountProperty.get());
            storage.queueUpForSave();
        });

        tradeCurrenciesInPaymentAccounts = userPayload.getPaymentAccounts().stream().flatMap(e -> e.getTradeCurrencies().stream()).collect(Collectors.toSet());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addPaymentAccount(PaymentAccount paymentAccount) {
        paymentAccountsAsObservable.add(paymentAccount);
        setCurrentPaymentAccount(paymentAccount);
    }

    public void removePaymentAccount(PaymentAccount paymentAccount) {
        paymentAccountsAsObservable.remove(paymentAccount);
    }

    public void setCurrentPaymentAccount(PaymentAccount paymentAccount) {
        currentPaymentAccountProperty.set(paymentAccount);
    }

    public boolean addAcceptedLanguageLocale(String localeCode) {
        if (!userPayload.getAcceptedLanguageLocaleCodes().contains(localeCode)) {
            boolean changed = userPayload.getAcceptedLanguageLocaleCodes().add(localeCode);
            if (changed)
                storage.queueUpForSave();
            return changed;
        } else {
            return false;
        }
    }

    public boolean removeAcceptedLanguageLocale(String languageLocaleCode) {
        boolean changed = userPayload.getAcceptedLanguageLocaleCodes().remove(languageLocaleCode);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    public void addAcceptedArbitrator(Arbitrator arbitrator) {
        if (!userPayload.getAcceptedArbitrators().contains(arbitrator) && !isMyOwnRegisteredArbitrator(arbitrator)) {
            boolean changed = userPayload.getAcceptedArbitrators().add(arbitrator);
            if (changed)
                storage.queueUpForSave();
        }
    }

    public void addAcceptedMediator(Mediator mediator) {
        if (!userPayload.getAcceptedMediators().contains(mediator) && !isMyOwnRegisteredMediator(mediator)) {
            boolean changed = userPayload.getAcceptedMediators().add(mediator);
            if (changed)
                storage.queueUpForSave();
        }
    }

    public boolean isMyOwnRegisteredArbitrator(Arbitrator arbitrator) {
        return arbitrator.equals(userPayload.getRegisteredArbitrator());
    }

    public boolean isMyOwnRegisteredMediator(Mediator mediator) {
        return mediator.equals(userPayload.getRegisteredArbitrator());
    }

    public void removeAcceptedArbitrator(Arbitrator arbitrator) {
        boolean changed = userPayload.getAcceptedArbitrators().remove(arbitrator);
        if (changed)
            storage.queueUpForSave();
    }

    public void clearAcceptedArbitrators() {
        userPayload.getAcceptedArbitrators().clear();
        storage.queueUpForSave();
    }

    public void removeAcceptedMediator(Mediator mediator) {
        boolean changed = userPayload.getAcceptedMediators().remove(mediator);
        if (changed)
            storage.queueUpForSave();
    }

    public void clearAcceptedMediators() {
        userPayload.getAcceptedMediators().clear();
        storage.queueUpForSave();
    }

    public void setRegisteredArbitrator(@Nullable Arbitrator arbitrator) {
        userPayload.setRegisteredArbitrator(arbitrator);
        storage.queueUpForSave();
    }

    public void setRegisteredMediator(@Nullable Mediator mediator) {
        userPayload.setRegisteredMediator(mediator);
        storage.queueUpForSave();
    }

    public void setDevelopersFilter(@Nullable Filter developersFilter) {
        userPayload.setDevelopersFilter(developersFilter);
        storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public PaymentAccount getPaymentAccount(String paymentAccountId) {
        Optional<PaymentAccount> optional = userPayload.getPaymentAccounts().stream().filter(e -> e.getId().equals(paymentAccountId)).findAny();
        if (optional.isPresent())
            return optional.get();
        else
            return null;
    }

    public String getAccountId() {
        return userPayload.getAccountID();
    }

   /* public boolean isRegistered() {
        return getAccountId() != null;
    }*/

    private PaymentAccount getCurrentPaymentAccount() {
        return userPayload.getCurrentPaymentAccount();
    }

    public ObjectProperty<PaymentAccount> currentPaymentAccountProperty() {
        return currentPaymentAccountProperty;
    }

    public Set<PaymentAccount> getPaymentAccounts() {
        return userPayload.getPaymentAccounts();
    }

    public ObservableSet<PaymentAccount> getPaymentAccountsAsObservable() {
        return paymentAccountsAsObservable;
    }

    @Nullable
    public Arbitrator getRegisteredArbitrator() {
        return userPayload.getRegisteredArbitrator();
    }

    @Nullable
    public Mediator getRegisteredMediator() {
        return userPayload.getRegisteredMediator();
    }

    public List<Arbitrator> getAcceptedArbitrators() {
        return userPayload.getAcceptedArbitrators();
    }

    public List<NodeAddress> getAcceptedArbitratorAddresses() {
        return userPayload.getAcceptedArbitrators().stream().map(Arbitrator::getNodeAddress).collect(Collectors.toList());
    }

    public List<Mediator> getAcceptedMediators() {
        return userPayload.getAcceptedMediators();
    }

    public List<NodeAddress> getAcceptedMediatorAddresses() {
        return userPayload.getAcceptedMediators().stream().map(Mediator::getNodeAddress).collect(Collectors.toList());
    }

    public List<String> getAcceptedLanguageLocaleCodes() {
        return userPayload.getAcceptedLanguageLocaleCodes() != null ? userPayload.getAcceptedLanguageLocaleCodes() : new ArrayList<>();
    }

    public Arbitrator getAcceptedArbitratorByAddress(NodeAddress nodeAddress) {
        Optional<Arbitrator> arbitratorOptional = userPayload.getAcceptedArbitrators().stream()
                .filter(e -> e.getNodeAddress().equals(nodeAddress))
                .findFirst();
        if (arbitratorOptional.isPresent())
            return arbitratorOptional.get();
        else
            return null;
    }

    public Mediator getAcceptedMediatorByAddress(NodeAddress nodeAddress) {
        Optional<Mediator> mediatorOptionalOptional = userPayload.getAcceptedMediators().stream()
                .filter(e -> e.getNodeAddress().equals(nodeAddress))
                .findFirst();
        if (mediatorOptionalOptional.isPresent())
            return mediatorOptionalOptional.get();
        else
            return null;
    }

    @Nullable
    public Filter getDevelopersFilter() {
        return userPayload.getDevelopersFilter();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

  /*  public Optional<TradeCurrency> getPaymentAccountForCurrency(TradeCurrency tradeCurrency) {
        return getPaymentAccounts().stream()
                .flatMap(e -> e.getTradeCurrencies().stream())
                .filter(e -> e.equals(tradeCurrency))
                .findFirst();
    }*/

    @Nullable
    public PaymentAccount findFirstPaymentAccountWithCurrency(TradeCurrency tradeCurrency) {
        for (PaymentAccount paymentAccount : userPayload.getPaymentAccounts()) {
            for (TradeCurrency tradeCurrency1 : paymentAccount.getTradeCurrencies()) {
                if (tradeCurrency1.equals(tradeCurrency))
                    return paymentAccount;
            }
        }
        return null;
    }

    public boolean hasMatchingLanguage(Arbitrator arbitrator) {
        if (arbitrator != null) {
            for (String acceptedCode : userPayload.getAcceptedLanguageLocaleCodes()) {
                for (String itemCode : arbitrator.getLanguageCodes()) {
                    if (acceptedCode.equals(itemCode))
                        return true;
                }
            }
        }
        return false;
    }

    public boolean hasPaymentAccountForCurrency(TradeCurrency tradeCurrency) {
        return findFirstPaymentAccountWithCurrency(tradeCurrency) != null;
    }

    public void setDevelopersAlert(@Nullable Alert developersAlert) {
        userPayload.setDevelopersAlert(developersAlert);
        storage.queueUpForSave();
    }

    @Nullable
    public Alert getDevelopersAlert() {
        return userPayload.getDevelopersAlert();
    }

    public void setDisplayedAlert(@Nullable Alert displayedAlert) {
        userPayload.setDisplayedAlert(displayedAlert);
        storage.queueUpForSave();
    }

    @Nullable
    public Alert getDisplayedAlert() {
        return userPayload.getDisplayedAlert();
    }
}
