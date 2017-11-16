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

package io.bisq.core.user;

import io.bisq.common.crypto.KeyRing;
import io.bisq.common.locale.LanguageUtil;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.storage.Storage;
import io.bisq.core.alert.Alert;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.arbitration.Mediator;
import io.bisq.core.filter.Filter;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.network.p2p.NodeAddress;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * The User is persisted locally.
 * It must never be transmitted over the wire (messageKeyPair contains private key!).
 */
@Slf4j
@AllArgsConstructor
public final class User implements PersistedDataHost {
    final private Storage<UserPayload> storage;
    final private KeyRing keyRing;

    private ObservableSet<PaymentAccount> paymentAccountsAsObservable;
    private ObjectProperty<PaymentAccount> currentPaymentAccountProperty;

    private UserPayload userPayload = new UserPayload();

    @Inject
    public User(Storage<UserPayload> storage, KeyRing keyRing) {
        this.storage = storage;
        this.keyRing = keyRing;
    }

    // for unit tests
    public User() {
        storage = null;
        keyRing = null;
    }

    @Override
    public void readPersisted() {
        UserPayload persisted = storage.initAndGetPersistedWithFileName("UserPayload", 100);
        userPayload = persisted != null ? persisted : new UserPayload();

        checkNotNull(userPayload.getPaymentAccounts(), "userPayload.getPaymentAccounts() must not be null");
        checkNotNull(userPayload.getAcceptedLanguageLocaleCodes(), "userPayload.getAcceptedLanguageLocaleCodes() must not be null");
        paymentAccountsAsObservable = FXCollections.observableSet(userPayload.getPaymentAccounts());
        currentPaymentAccountProperty = new SimpleObjectProperty<>(userPayload.getCurrentPaymentAccount());
        userPayload.setAccountId(String.valueOf(Math.abs(keyRing.getPubKeyRing().hashCode())));

        // language setup
        if (!userPayload.getAcceptedLanguageLocaleCodes().contains(LanguageUtil.getDefaultLanguageLocaleAsCode()))
            userPayload.getAcceptedLanguageLocaleCodes().add(LanguageUtil.getDefaultLanguageLocaleAsCode());
        String english = LanguageUtil.getEnglishLanguageLocaleCode();
        if (!userPayload.getAcceptedLanguageLocaleCodes().contains(english))
            userPayload.getAcceptedLanguageLocaleCodes().add(english);

        paymentAccountsAsObservable.addListener((SetChangeListener<PaymentAccount>) change -> {
            userPayload.setPaymentAccounts(new HashSet<>(paymentAccountsAsObservable));
            persist();
        });
        currentPaymentAccountProperty.addListener((ov) -> {
            userPayload.setCurrentPaymentAccount(currentPaymentAccountProperty.get());
            persist();
        });

    }

    private void persist() {
        storage.queueUpForSave(userPayload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /*  public Optional<TradeCurrency> getPaymentAccountForCurrency(TradeCurrency tradeCurrency) {
          return getPaymentAccounts().stream()
                  .flatMap(e -> e.getTradeCurrencies().stream())
                  .filter(e -> e.equals(tradeCurrency))
                  .findFirst();
      }*/

    @Nullable
    public Arbitrator getAcceptedArbitratorByAddress(NodeAddress nodeAddress) {
        final List<Arbitrator> acceptedArbitrators = userPayload.getAcceptedArbitrators();
        if (acceptedArbitrators != null) {
            Optional<Arbitrator> arbitratorOptional = acceptedArbitrators.stream()
                    .filter(e -> e.getNodeAddress().equals(nodeAddress))
                    .findFirst();
            if (arbitratorOptional.isPresent())
                return arbitratorOptional.get();
            else
                return null;
        } else {
            return null;
        }
    }

    @Nullable
    public Mediator getAcceptedMediatorByAddress(NodeAddress nodeAddress) {
        final List<Mediator> acceptedMediators = userPayload.getAcceptedMediators();
        if (acceptedMediators != null) {
            Optional<Mediator> mediatorOptionalOptional = acceptedMediators.stream()
                    .filter(e -> e.getNodeAddress().equals(nodeAddress))
                    .findFirst();
            if (mediatorOptionalOptional.isPresent())
                return mediatorOptionalOptional.get();
            else
                return null;
        } else {
            return null;
        }
    }

    @Nullable
    public PaymentAccount findFirstPaymentAccountWithCurrency(TradeCurrency tradeCurrency) {
        if (userPayload.getPaymentAccounts() != null) {
            for (PaymentAccount paymentAccount : userPayload.getPaymentAccounts()) {
                for (TradeCurrency currency : paymentAccount.getTradeCurrencies()) {
                    if (currency.equals(tradeCurrency))
                        return paymentAccount;
                }
            }
            return null;
        } else {
            return null;
        }
    }

    public boolean hasMatchingLanguage(Arbitrator arbitrator) {
        final List<String> codes = userPayload.getAcceptedLanguageLocaleCodes();
        if (arbitrator != null && codes != null) {
            for (String acceptedCode : codes) {
                for (String itemCode : arbitrator.getLanguageCodes()) {
                    if (acceptedCode.equals(itemCode))
                        return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }

    public boolean hasPaymentAccountForCurrency(TradeCurrency tradeCurrency) {
        return findFirstPaymentAccountWithCurrency(tradeCurrency) != null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Collection operations
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addPaymentAccount(PaymentAccount paymentAccount) {
        boolean changed = paymentAccountsAsObservable.add(paymentAccount);
        setCurrentPaymentAccount(paymentAccount);
        if (changed)
            persist();
    }

    public void removePaymentAccount(PaymentAccount paymentAccount) {
        boolean changed = paymentAccountsAsObservable.remove(paymentAccount);
        if (changed)
            persist();
    }

    public boolean addAcceptedLanguageLocale(String localeCode) {
        final List<String> codes = userPayload.getAcceptedLanguageLocaleCodes();
        if (codes != null && !codes.contains(localeCode)) {
            boolean changed = codes.add(localeCode);
            if (changed)
                persist();
            return changed;
        } else {
            return false;
        }
    }

    public boolean removeAcceptedLanguageLocale(String languageLocaleCode) {
        boolean changed = userPayload.getAcceptedLanguageLocaleCodes() != null &&
                userPayload.getAcceptedLanguageLocaleCodes().remove(languageLocaleCode);
        if (changed)
            persist();
        return changed;
    }

    public boolean addAcceptedArbitrator(Arbitrator arbitrator) {
        final List<Arbitrator> arbitrators = userPayload.getAcceptedArbitrators();
        if (arbitrators != null && !arbitrators.contains(arbitrator) && !isMyOwnRegisteredArbitrator(arbitrator)) {
            boolean changed = arbitrators.add(arbitrator);
            if (changed)
                persist();
            return changed;
        } else {
            return false;
        }
    }

    public boolean addAcceptedMediator(Mediator mediator) {
        final List<Mediator> mediators = userPayload.getAcceptedMediators();
        if (mediators != null && !mediators.contains(mediator) && !isMyOwnRegisteredMediator(mediator)) {
            boolean changed = mediators.add(mediator);
            if (changed)
                persist();
            return changed;
        } else {
            return false;
        }
    }


    public void removeAcceptedArbitrator(Arbitrator arbitrator) {
        if (userPayload.getAcceptedArbitrators() != null) {
            boolean changed = userPayload.getAcceptedArbitrators().remove(arbitrator);
            if (changed)
                persist();
        }
    }

    public void clearAcceptedArbitrators() {
        if (userPayload.getAcceptedArbitrators() != null) {
            userPayload.getAcceptedArbitrators().clear();
            persist();
        }
    }

    public void removeAcceptedMediator(Mediator mediator) {
        if (userPayload.getAcceptedMediators() != null) {
            boolean changed = userPayload.getAcceptedMediators().remove(mediator);
            if (changed)
                persist();
        }
    }

    public void clearAcceptedMediators() {
        if (userPayload.getAcceptedMediators() != null) {
            userPayload.getAcceptedMediators().clear();
            persist();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setCurrentPaymentAccount(PaymentAccount paymentAccount) {
        currentPaymentAccountProperty.set(paymentAccount);
        persist();
    }

    public void setRegisteredArbitrator(@Nullable Arbitrator arbitrator) {
        userPayload.setRegisteredArbitrator(arbitrator);
        persist();
    }

    public void setRegisteredMediator(@Nullable Mediator mediator) {
        userPayload.setRegisteredMediator(mediator);
        persist();
    }

    public void setDevelopersFilter(@Nullable Filter developersFilter) {
        userPayload.setDevelopersFilter(developersFilter);
        persist();
    }

    public void setDevelopersAlert(@Nullable Alert developersAlert) {
        userPayload.setDevelopersAlert(developersAlert);
        persist();
    }

    public void setDisplayedAlert(@Nullable Alert displayedAlert) {
        userPayload.setDisplayedAlert(displayedAlert);
        persist();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public PaymentAccount getPaymentAccount(String paymentAccountId) {
        Optional<PaymentAccount> optional = userPayload.getPaymentAccounts() != null ?
                userPayload.getPaymentAccounts().stream().filter(e -> e.getId().equals(paymentAccountId)).findAny() :
                Optional.<PaymentAccount>empty();
        if (optional.isPresent())
            return optional.get();
        else
            return null;
    }

    public String getAccountId() {
        return userPayload.getAccountId();
    }

    private PaymentAccount getCurrentPaymentAccount() {
        return userPayload.getCurrentPaymentAccount();
    }

    public ReadOnlyObjectProperty<PaymentAccount> currentPaymentAccountProperty() {
        return currentPaymentAccountProperty;
    }

    @Nullable
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

    @Nullable
    public List<Arbitrator> getAcceptedArbitrators() {
        return userPayload.getAcceptedArbitrators();
    }

    @Nullable
    public List<NodeAddress> getAcceptedArbitratorAddresses() {
        return userPayload.getAcceptedArbitrators() != null ? userPayload.getAcceptedArbitrators().stream().map(Arbitrator::getNodeAddress).collect(Collectors.toList()) : null;
    }

    @Nullable
    public List<Mediator> getAcceptedMediators() {
        return userPayload.getAcceptedMediators();
    }

    @Nullable
    public List<NodeAddress> getAcceptedMediatorAddresses() {
        return userPayload.getAcceptedMediators() != null ? userPayload.getAcceptedMediators().stream().map(Mediator::getNodeAddress).collect(Collectors.toList()) : null;
    }

    public List<String> getAcceptedLanguageLocaleCodes() {
        return userPayload.getAcceptedLanguageLocaleCodes() != null ? userPayload.getAcceptedLanguageLocaleCodes() : new ArrayList<>();
    }

    @Nullable
    public Filter getDevelopersFilter() {
        return userPayload.getDevelopersFilter();
    }

    @Nullable
    public Alert getDevelopersAlert() {
        return userPayload.getDevelopersAlert();
    }

    @Nullable
    public Alert getDisplayedAlert() {
        return userPayload.getDisplayedAlert();
    }

    public boolean isMyOwnRegisteredArbitrator(Arbitrator arbitrator) {
        return arbitrator.equals(userPayload.getRegisteredArbitrator());
    }

    public boolean isMyOwnRegisteredMediator(Mediator mediator) {
        return mediator.equals(userPayload.getRegisteredMediator());
    }
}
