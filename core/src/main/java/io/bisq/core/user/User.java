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

import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.locale.LanguageUtil;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.proto.ProtoHelper;
import io.bisq.common.storage.Storage;
import io.bisq.core.alert.Alert;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.arbitration.Mediator;
import io.bisq.core.filter.Filter;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.proto.ProtoUtil;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
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
public final class User implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    // persisted fields
    private UserVO userVO = new UserVO();

    // Transient immutable fields
    transient final private Storage<UserVO> storage;
    transient private Set<TradeCurrency> tradeCurrenciesInPaymentAccounts;

    // Observable wrappers
    transient final private ObservableSet<PaymentAccount> paymentAccountsAsObservable = FXCollections.observableSet(userVO.getPaymentAccounts());
    transient final private ObjectProperty<PaymentAccount> currentPaymentAccountProperty = new SimpleObjectProperty<>(userVO.getCurrentPaymentAccount());


    @Inject
    public User(Storage<UserVO> storage, KeyRing keyRing) throws NoSuchAlgorithmException {
        this.storage = storage;
        userVO.setAccountID(String.valueOf(Math.abs(keyRing.getPubKeyRing().hashCode())));
        // language setup
        userVO.getAcceptedLanguageLocaleCodes().add(LanguageUtil.getDefaultLanguageLocaleAsCode());
        String english = LanguageUtil.getEnglishLanguageLocaleCode();
        if (!userVO.getAcceptedLanguageLocaleCodes().contains(english))
            userVO.getAcceptedLanguageLocaleCodes().add(english);
    }

    // for unit tests
    public User() {
        this.storage = null;
    }

    public void init() {
        UserVO persisted = storage.initAndGetPersisted(userVO);
        if (persisted != null) {
            userVO = persisted;
            paymentAccountsAsObservable.addAll(userVO.getPaymentAccounts());
            currentPaymentAccountProperty.set(userVO.getCurrentPaymentAccount());
        }
        storage.queueUpForSave();

        // Use that to guarantee update of the serializable field and to make a storage update in case of a change
        paymentAccountsAsObservable.addListener((SetChangeListener<PaymentAccount>) change -> {
            userVO.setPaymentAccounts(new HashSet<>(paymentAccountsAsObservable));
            tradeCurrenciesInPaymentAccounts = userVO.getPaymentAccounts().stream().flatMap(e -> e.getTradeCurrencies().stream()).collect(Collectors.toSet());
            storage.queueUpForSave();
        });
        currentPaymentAccountProperty.addListener((ov) -> {
            userVO.setCurrentPaymentAccount(currentPaymentAccountProperty.get());
            storage.queueUpForSave();
        });

        tradeCurrenciesInPaymentAccounts = userVO.getPaymentAccounts().stream().flatMap(e -> e.getTradeCurrencies().stream()).collect(Collectors.toSet());
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
        if (!userVO.getAcceptedLanguageLocaleCodes().contains(localeCode)) {
            boolean changed = userVO.getAcceptedLanguageLocaleCodes().add(localeCode);
            if (changed)
                storage.queueUpForSave();
            return changed;
        } else {
            return false;
        }
    }

    public boolean removeAcceptedLanguageLocale(String languageLocaleCode) {
        boolean changed = userVO.getAcceptedLanguageLocaleCodes().remove(languageLocaleCode);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    public void addAcceptedArbitrator(Arbitrator arbitrator) {
        if (!userVO.getAcceptedArbitrators().contains(arbitrator) && !isMyOwnRegisteredArbitrator(arbitrator)) {
            boolean changed = userVO.getAcceptedArbitrators().add(arbitrator);
            if (changed)
                storage.queueUpForSave();
        }
    }

    public void addAcceptedMediator(Mediator mediator) {
        if (!userVO.getAcceptedMediators().contains(mediator) && !isMyOwnRegisteredMediator(mediator)) {
            boolean changed = userVO.getAcceptedMediators().add(mediator);
            if (changed)
                storage.queueUpForSave();
        }
    }

    public boolean isMyOwnRegisteredArbitrator(Arbitrator arbitrator) {
        return arbitrator.equals(userVO.getRegisteredArbitrator());
    }

    public boolean isMyOwnRegisteredMediator(Mediator mediator) {
        return mediator.equals(userVO.getRegisteredArbitrator());
    }

    public void removeAcceptedArbitrator(Arbitrator arbitrator) {
        boolean changed = userVO.getAcceptedArbitrators().remove(arbitrator);
        if (changed)
            storage.queueUpForSave();
    }

    public void clearAcceptedArbitrators() {
        userVO.getAcceptedArbitrators().clear();
        storage.queueUpForSave();
    }

    public void removeAcceptedMediator(Mediator mediator) {
        boolean changed = userVO.getAcceptedMediators().remove(mediator);
        if (changed)
            storage.queueUpForSave();
    }

    public void clearAcceptedMediators() {
        userVO.getAcceptedMediators().clear();
        storage.queueUpForSave();
    }

    public void setRegisteredArbitrator(@Nullable Arbitrator arbitrator) {
        userVO.setRegisteredArbitrator(arbitrator);
        storage.queueUpForSave();
    }

    public void setRegisteredMediator(@Nullable Mediator mediator) {
        userVO.setRegisteredMediator(mediator);
        storage.queueUpForSave();
    }

    public void setDevelopersFilter(@Nullable Filter developersFilter) {
        userVO.setDevelopersFilter(developersFilter);
        storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public PaymentAccount getPaymentAccount(String paymentAccountId) {
        Optional<PaymentAccount> optional = userVO.getPaymentAccounts().stream().filter(e -> e.getId().equals(paymentAccountId)).findAny();
        if (optional.isPresent())
            return optional.get();
        else
            return null;
    }

    public String getAccountId() {
        return userVO.getAccountID();
    }

   /* public boolean isRegistered() {
        return getAccountId() != null;
    }*/

    private PaymentAccount getCurrentPaymentAccount() {
        return userVO.getCurrentPaymentAccount();
    }

    public ObjectProperty<PaymentAccount> currentPaymentAccountProperty() {
        return currentPaymentAccountProperty;
    }

    public Set<PaymentAccount> getPaymentAccounts() {
        return userVO.getPaymentAccounts();
    }

    public ObservableSet<PaymentAccount> getPaymentAccountsAsObservable() {
        return paymentAccountsAsObservable;
    }

    @Nullable
    public Arbitrator getRegisteredArbitrator() {
        return userVO.getRegisteredArbitrator();
    }

    @Nullable
    public Mediator getRegisteredMediator() {
        return userVO.getRegisteredMediator();
    }

    public List<Arbitrator> getAcceptedArbitrators() {
        return userVO.getAcceptedArbitrators();
    }

    public List<NodeAddress> getAcceptedArbitratorAddresses() {
        return userVO.getAcceptedArbitrators().stream().map(Arbitrator::getNodeAddress).collect(Collectors.toList());
    }

    public List<Mediator> getAcceptedMediators() {
        return userVO.getAcceptedMediators();
    }

    public List<NodeAddress> getAcceptedMediatorAddresses() {
        return userVO.getAcceptedMediators().stream().map(Mediator::getNodeAddress).collect(Collectors.toList());
    }

    public List<String> getAcceptedLanguageLocaleCodes() {
        return userVO.getAcceptedLanguageLocaleCodes() != null ? userVO.getAcceptedLanguageLocaleCodes() : new ArrayList<>();
    }

    public Arbitrator getAcceptedArbitratorByAddress(NodeAddress nodeAddress) {
        Optional<Arbitrator> arbitratorOptional = userVO.getAcceptedArbitrators().stream()
                .filter(e -> e.getNodeAddress().equals(nodeAddress))
                .findFirst();
        if (arbitratorOptional.isPresent())
            return arbitratorOptional.get();
        else
            return null;
    }

    public Mediator getAcceptedMediatorByAddress(NodeAddress nodeAddress) {
        Optional<Mediator> mediatorOptionalOptional = userVO.getAcceptedMediators().stream()
                .filter(e -> e.getNodeAddress().equals(nodeAddress))
                .findFirst();
        if (mediatorOptionalOptional.isPresent())
            return mediatorOptionalOptional.get();
        else
            return null;
    }

    @Nullable
    public Filter getDevelopersFilter() {
        return userVO.getDevelopersFilter();
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
        for (PaymentAccount paymentAccount : userVO.getPaymentAccounts()) {
            for (TradeCurrency tradeCurrency1 : paymentAccount.getTradeCurrencies()) {
                if (tradeCurrency1.equals(tradeCurrency))
                    return paymentAccount;
            }
        }
        return null;
    }

    public boolean hasMatchingLanguage(Arbitrator arbitrator) {
        if (arbitrator != null) {
            for (String acceptedCode : userVO.getAcceptedLanguageLocaleCodes()) {
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
        userVO.setDevelopersAlert(developersAlert);
        storage.queueUpForSave();
    }

    @Nullable
    public Alert getDevelopersAlert() {
        return userVO.getDevelopersAlert();
    }

    public void setDisplayedAlert(@Nullable Alert displayedAlert) {
        userVO.setDisplayedAlert(displayedAlert);
        storage.queueUpForSave();
    }

    @Nullable
    public Alert getDisplayedAlert() {
        return userVO.getDisplayedAlert();
    }

    @Override
    public Message toProto() {
        return userVO.toProto();
    }
}
