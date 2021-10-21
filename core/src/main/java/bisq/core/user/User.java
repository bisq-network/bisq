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
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.LanguageUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.notifications.alerts.market.MarketAlertFilter;
import bisq.core.notifications.alerts.price.PriceAlertFilter;
import bisq.core.payment.BsqSwapAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.support.dispute.arbitration.arbitrator.Arbitrator;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.support.dispute.refund.refundagent.RefundAgent;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

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
    private final PersistenceManager<UserPayload> persistenceManager;
    private final KeyRing keyRing;

    private ObservableSet<PaymentAccount> paymentAccountsAsObservable;
    private ObjectProperty<PaymentAccount> currentPaymentAccountProperty;

    private UserPayload userPayload = new UserPayload();
    private boolean isPaymentAccountImport = false;

    @Inject
    public User(PersistenceManager<UserPayload> persistenceManager, KeyRing keyRing) {
        this.persistenceManager = persistenceManager;
        this.keyRing = keyRing;
    }

    // for unit tests
    public User() {
        persistenceManager = null;
        keyRing = null;
    }

    @Override
    public void readPersisted(Runnable completeHandler) {
        checkNotNull(persistenceManager).readPersisted("UserPayload",
                persisted -> {
                    userPayload = persisted;
                    init();
                    completeHandler.run();
                },
                () -> {
                    init();
                    completeHandler.run();
                });
    }

    private void init() {
        checkNotNull(persistenceManager).initialize(userPayload, PersistenceManager.Source.PRIVATE);

        checkNotNull(userPayload.getPaymentAccounts(), "userPayload.getPaymentAccounts() must not be null");
        checkNotNull(userPayload.getAcceptedLanguageLocaleCodes(), "userPayload.getAcceptedLanguageLocaleCodes() must not be null");
        paymentAccountsAsObservable = FXCollections.observableSet(userPayload.getPaymentAccounts());
        currentPaymentAccountProperty = new SimpleObjectProperty<>(userPayload.getCurrentPaymentAccount());
        userPayload.setAccountId(String.valueOf(Math.abs(checkNotNull(keyRing).getPubKeyRing().hashCode())));

        // language setup
        if (!userPayload.getAcceptedLanguageLocaleCodes().contains(LanguageUtil.getDefaultLanguageLocaleAsCode()))
            userPayload.getAcceptedLanguageLocaleCodes().add(LanguageUtil.getDefaultLanguageLocaleAsCode());
        String english = LanguageUtil.getEnglishLanguageLocaleCode();
        if (!userPayload.getAcceptedLanguageLocaleCodes().contains(english))
            userPayload.getAcceptedLanguageLocaleCodes().add(english);

        paymentAccountsAsObservable.addListener((SetChangeListener<PaymentAccount>) change -> {
            userPayload.setPaymentAccounts(new HashSet<>(paymentAccountsAsObservable));
            requestPersistence();
        });
        currentPaymentAccountProperty.addListener((ov) -> {
            userPayload.setCurrentPaymentAccount(currentPaymentAccountProperty.get());
            requestPersistence();
        });

        // We create a default placeholder account for BSQ swaps. The account has not content, it is just used
        // so that the BsqSwap use case fits into the current domain
        addBsqSwapAccount();

        requestPersistence();
    }

    private void addBsqSwapAccount() {
        checkNotNull(userPayload.getPaymentAccounts(), "userPayload.getPaymentAccounts() must not be null");
        if (userPayload.getPaymentAccounts().stream()
                .anyMatch(paymentAccount -> paymentAccount instanceof BsqSwapAccount))
            return;

        var account = new BsqSwapAccount();
        account.init();
        account.setAccountName(Res.get("BSQ_SWAP"));
        account.setSingleTradeCurrency(new CryptoCurrency("BSQ", "BSQ"));
        addPaymentAccount(account);
    }

    public void requestPersistence() {
        if (persistenceManager != null)
            persistenceManager.requestPersistence();
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

    public void addPaymentAccountIfNotExists(PaymentAccount paymentAccount) {
        if (!paymentAccountExists(paymentAccount)) {
            addPaymentAccount(paymentAccount);
            requestPersistence();
        }
    }

    public void addPaymentAccount(PaymentAccount paymentAccount) {
        paymentAccount.onAddToUser();

        boolean changed = paymentAccountsAsObservable.add(paymentAccount);
        setCurrentPaymentAccount(paymentAccount);
        if (changed)
            requestPersistence();
    }

    public void addImportedPaymentAccounts(Collection<PaymentAccount> paymentAccounts) {
        isPaymentAccountImport = true;

        boolean changed = paymentAccountsAsObservable.addAll(paymentAccounts);
        paymentAccounts.stream().findFirst().ifPresent(this::setCurrentPaymentAccount);
        if (changed)
            requestPersistence();

        isPaymentAccountImport = false;
    }

    public void removePaymentAccount(PaymentAccount paymentAccount) {
        boolean changed = paymentAccountsAsObservable.remove(paymentAccount);
        if (changed)
            requestPersistence();
    }

    public boolean addAcceptedArbitrator(Arbitrator arbitrator) {
        List<Arbitrator> arbitrators = userPayload.getAcceptedArbitrators();
        if (arbitrators != null && !arbitrators.contains(arbitrator) && !isMyOwnRegisteredArbitrator(arbitrator)) {
            arbitrators.add(arbitrator);
            requestPersistence();
            return true;
        } else {
            return false;
        }
    }

    public void removeAcceptedArbitrator(Arbitrator arbitrator) {
        if (userPayload.getAcceptedArbitrators() != null) {
            boolean changed = userPayload.getAcceptedArbitrators().remove(arbitrator);
            if (changed)
                requestPersistence();
        }
    }

    public void clearAcceptedArbitrators() {
        if (userPayload.getAcceptedArbitrators() != null) {
            userPayload.getAcceptedArbitrators().clear();
            requestPersistence();
        }
    }

    public boolean addAcceptedMediator(Mediator mediator) {
        List<Mediator> mediators = userPayload.getAcceptedMediators();
        if (mediators != null && !mediators.contains(mediator) && !isMyOwnRegisteredMediator(mediator)) {
            mediators.add(mediator);
            requestPersistence();
            return true;
        } else {
            return false;
        }
    }

    public void removeAcceptedMediator(Mediator mediator) {
        if (userPayload.getAcceptedMediators() != null) {
            boolean changed = userPayload.getAcceptedMediators().remove(mediator);
            if (changed)
                requestPersistence();
        }
    }

    public void clearAcceptedMediators() {
        if (userPayload.getAcceptedMediators() != null) {
            userPayload.getAcceptedMediators().clear();
            requestPersistence();
        }
    }

    public boolean addAcceptedRefundAgent(RefundAgent refundAgent) {
        List<RefundAgent> refundAgents = userPayload.getAcceptedRefundAgents();
        if (refundAgents != null && !refundAgents.contains(refundAgent) && !isMyOwnRegisteredRefundAgent(refundAgent)) {
            refundAgents.add(refundAgent);
            requestPersistence();
            return true;
        } else {
            return false;
        }
    }

    public void removeAcceptedRefundAgent(RefundAgent refundAgent) {
        if (userPayload.getAcceptedRefundAgents() != null) {
            boolean changed = userPayload.getAcceptedRefundAgents().remove(refundAgent);
            if (changed)
                requestPersistence();
        }
    }

    public void clearAcceptedRefundAgents() {
        if (userPayload.getAcceptedRefundAgents() != null) {
            userPayload.getAcceptedRefundAgents().clear();
            requestPersistence();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setCurrentPaymentAccount(PaymentAccount paymentAccount) {
        currentPaymentAccountProperty.set(paymentAccount);
        requestPersistence();
    }

    public void setRegisteredArbitrator(@Nullable Arbitrator arbitrator) {
        userPayload.setRegisteredArbitrator(arbitrator);
        requestPersistence();
    }

    public void setRegisteredMediator(@Nullable Mediator mediator) {
        userPayload.setRegisteredMediator(mediator);
        requestPersistence();
    }

    public void setRegisteredRefundAgent(@Nullable RefundAgent refundAgent) {
        userPayload.setRegisteredRefundAgent(refundAgent);
        requestPersistence();
    }

    public void setDevelopersFilter(@Nullable Filter developersFilter) {
        userPayload.setDevelopersFilter(developersFilter);
        requestPersistence();
    }

    public void setDevelopersAlert(@Nullable Alert developersAlert) {
        userPayload.setDevelopersAlert(developersAlert);
        requestPersistence();
    }

    public void setDisplayedAlert(@Nullable Alert displayedAlert) {
        userPayload.setDisplayedAlert(displayedAlert);
        requestPersistence();
    }

    public void addMarketAlertFilter(MarketAlertFilter filter) {
        getMarketAlertFilters().add(filter);
        requestPersistence();
    }

    public void removeMarketAlertFilter(MarketAlertFilter filter) {
        getMarketAlertFilters().remove(filter);
        requestPersistence();
    }

    public void setPriceAlertFilter(PriceAlertFilter filter) {
        userPayload.setPriceAlertFilter(filter);
        requestPersistence();
    }

    public void removePriceAlertFilter() {
        userPayload.setPriceAlertFilter(null);
        requestPersistence();
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
        return userPayload.getAcceptedArbitrators() != null ?
                userPayload.getAcceptedArbitrators().stream().map(Arbitrator::getNodeAddress).collect(Collectors.toList()) :
                null;
    }

    @Nullable
    public List<NodeAddress> getAcceptedMediatorAddresses() {
        return userPayload.getAcceptedMediators() != null ?
                userPayload.getAcceptedMediators().stream().map(Mediator::getNodeAddress).collect(Collectors.toList()) :
                null;
    }

    @Nullable
    public List<NodeAddress> getAcceptedRefundAgentAddresses() {
        return userPayload.getAcceptedRefundAgents() != null ?
                userPayload.getAcceptedRefundAgents().stream().map(RefundAgent::getNodeAddress).collect(Collectors.toList()) :
                null;
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

    private boolean paymentAccountExists(PaymentAccount paymentAccount) {
        return getPaymentAccountsAsObservable().stream().anyMatch(e -> e.equals(paymentAccount));
    }

    public Cookie getCookie() {
        return userPayload.getCookie();
    }
}
