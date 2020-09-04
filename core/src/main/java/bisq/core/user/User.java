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

package bisq.core.user;

import bisq.core.alert.Alert;
import bisq.core.filter.Filter;
import bisq.core.locale.LanguageUtil;
import bisq.core.locale.TradeCurrency;
import bisq.core.notifications.alerts.market.MarketAlertFilter;
import bisq.core.notifications.alerts.price.PriceAlertFilter;
import bisq.core.payment.PaymentAccount;
import bisq.core.support.dispute.arbitration.arbitrator.Arbitrator;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.support.dispute.refund.refundagent.RefundAgent;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The User is persisted locally.
 * It must never be transmitted over the wire (messageKeyPair contains private key!).
 */
@Slf4j
@AllArgsConstructor
@Singleton
public class User implements PersistedDataHost {
    final private Storage<UserPayload> storage;
    final private KeyRing keyRing;

    private ObservableSet<PaymentAccount> paymentAccountsAsObservable;
    private ObjectProperty<PaymentAccount> currentPaymentAccountProperty;

    private UserPayload userPayload = new UserPayload();
    private boolean isPaymentAccountImport = false;

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

    public void persist() {
        if (storage != null)
            storage.queueUpForSave(userPayload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public Arbitrator getAcceptedArbitratorByAddress(NodeAddress nodeAddress) {
        final List<Arbitrator> acceptedArbitrators = userPayload.getAcceptedArbitrators();
        if (acceptedArbitrators != null) {
            Optional<Arbitrator> arbitratorOptional = acceptedArbitrators.stream()
                    .filter(e -> e.getNodeAddress().equals(nodeAddress))
                    .findFirst();
            return arbitratorOptional.orElse(null);
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
            return mediatorOptionalOptional.orElse(null);
        } else {
            return null;
        }
    }

    @Nullable
    public RefundAgent getAcceptedRefundAgentByAddress(NodeAddress nodeAddress) {
        final List<RefundAgent> acceptedRefundAgents = userPayload.getAcceptedRefundAgents();
        if (acceptedRefundAgents != null) {
            Optional<RefundAgent> refundAgentOptional = acceptedRefundAgents.stream()
                    .filter(e -> e.getNodeAddress().equals(nodeAddress))
                    .findFirst();
            return refundAgentOptional.orElse(null);
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

    public void addImportedPaymentAccounts(Collection<PaymentAccount> paymentAccounts) {
        isPaymentAccountImport = true;

        boolean changed = paymentAccountsAsObservable.addAll(paymentAccounts);
        setCurrentPaymentAccount(paymentAccounts.stream().findFirst().get());
        if (changed)
            persist();

        isPaymentAccountImport = false;
    }

    public void removePaymentAccount(PaymentAccount paymentAccount) {
        boolean changed = paymentAccountsAsObservable.remove(paymentAccount);
        if (changed)
            persist();
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

    public boolean addAcceptedRefundAgent(RefundAgent refundAgent) {
        final List<RefundAgent> refundAgents = userPayload.getAcceptedRefundAgents();
        if (refundAgents != null && !refundAgents.contains(refundAgent) && !isMyOwnRegisteredRefundAgent(refundAgent)) {
            boolean changed = refundAgents.add(refundAgent);
            if (changed)
                persist();
            return changed;
        } else {
            return false;
        }
    }

    public void removeAcceptedRefundAgent(RefundAgent refundAgent) {
        if (userPayload.getAcceptedRefundAgents() != null) {
            boolean changed = userPayload.getAcceptedRefundAgents().remove(refundAgent);
            if (changed)
                persist();
        }
    }

    public void clearAcceptedRefundAgents() {
        if (userPayload.getAcceptedRefundAgents() != null) {
            userPayload.getAcceptedRefundAgents().clear();
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

    public void setRegisteredRefundAgent(@Nullable RefundAgent refundAgent) {
        userPayload.setRegisteredRefundAgent(refundAgent);
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

    public void addMarketAlertFilter(MarketAlertFilter filter) {
        getMarketAlertFilters().add(filter);
        persist();
    }

    public void removeMarketAlertFilter(MarketAlertFilter filter) {
        getMarketAlertFilters().remove(filter);
        persist();
    }

    public void setPriceAlertFilter(PriceAlertFilter filter) {
        userPayload.setPriceAlertFilter(filter);
        persist();
    }

    public void removePriceAlertFilter() {
        userPayload.setPriceAlertFilter(null);
        persist();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public PaymentAccount getPaymentAccount(String paymentAccountId) {
        Optional<PaymentAccount> optional = userPayload.getPaymentAccounts() != null ?
                userPayload.getPaymentAccounts().stream().filter(e -> e.getId().equals(paymentAccountId)).findAny() :
                Optional.empty();
        return optional.orElse(null);
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


    /**
     * If this user is an arbitrator it returns the registered arbitrator.
     *
     * @return The arbitrator registered for this user
     */
    @Nullable
    public Arbitrator getRegisteredArbitrator() {
        return userPayload.getRegisteredArbitrator();
    }

    @Nullable
    public Mediator getRegisteredMediator() {
        return userPayload.getRegisteredMediator();
    }

    @Nullable
    public RefundAgent getRegisteredRefundAgent() {
        return userPayload.getRegisteredRefundAgent();
    }


    //TODO
    @Nullable
    public List<Arbitrator> getAcceptedArbitrators() {
        return userPayload.getAcceptedArbitrators();
    }

    @Nullable
    public List<Mediator> getAcceptedMediators() {
        return userPayload.getAcceptedMediators();
    }

    @Nullable
    public List<RefundAgent> getAcceptedRefundAgents() {
        return userPayload.getAcceptedRefundAgents();
    }

    @Nullable
    public List<NodeAddress> getAcceptedArbitratorAddresses() {
        return userPayload.getAcceptedArbitrators() != null ? userPayload.getAcceptedArbitrators().stream().map(Arbitrator::getNodeAddress).collect(Collectors.toList()) : null;
    }

    @Nullable
    public List<NodeAddress> getAcceptedMediatorAddresses() {
        return userPayload.getAcceptedMediators() != null ? userPayload.getAcceptedMediators().stream().map(Mediator::getNodeAddress).collect(Collectors.toList()) : null;
    }

    @Nullable
    public List<NodeAddress> getAcceptedRefundAgentAddresses() {
        return userPayload.getAcceptedRefundAgents() != null ? userPayload.getAcceptedRefundAgents().stream().map(RefundAgent::getNodeAddress).collect(Collectors.toList()) : null;
    }

    public boolean hasAcceptedArbitrators() {
        return getAcceptedArbitrators() != null && !getAcceptedArbitrators().isEmpty();
    }

    public boolean hasAcceptedMediators() {
        return getAcceptedMediators() != null && !getAcceptedMediators().isEmpty();
    }

    public boolean hasAcceptedRefundAgents() {
        return getAcceptedRefundAgents() != null && !getAcceptedRefundAgents().isEmpty();
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

    public boolean isMyOwnRegisteredRefundAgent(RefundAgent refundAgent) {
        return refundAgent.equals(userPayload.getRegisteredRefundAgent());
    }

    public List<MarketAlertFilter> getMarketAlertFilters() {
        return userPayload.getMarketAlertFilters();
    }

    @Nullable
    public PriceAlertFilter getPriceAlertFilter() {
        return userPayload.getPriceAlertFilter();
    }

    public boolean isPaymentAccountImport() {
        return isPaymentAccountImport;
    }
}
